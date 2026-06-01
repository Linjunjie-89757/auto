import http from 'node:http'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '..')
const port = Number(process.env.PORT || process.env.CAPTURE_PORT || 8787)
const host = process.env.HOST || '127.0.0.1'
const logDir = path.resolve(process.env.CAPTURE_LOG_DIR || path.join(repoRoot, '.tmp', 'openai-capture'))

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, '-')
}

function redactHeaders(headers) {
  const safe = { ...headers }
  for (const key of Object.keys(safe)) {
    if (['authorization', 'x-api-key', 'api-key'].includes(key.toLowerCase())) {
      safe[key] = '[redacted]'
    }
  }
  return safe
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = []
    req.on('data', chunk => chunks.push(chunk))
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    req.on('error', reject)
  })
}

async function capture(req, bodyText) {
  await mkdir(logDir, { recursive: true })
  let body = bodyText
  try {
    body = JSON.parse(bodyText || '{}')
  } catch {
    // Keep raw body if it is not JSON.
  }
  const record = {
    capturedAt: new Date().toISOString(),
    method: req.method,
    url: req.url,
    headers: redactHeaders(req.headers),
    body,
  }
  const fileName = `${nowStamp()}-${req.method}-${req.url.replace(/[^\w.-]+/g, '_')}.json`
  const filePath = path.join(logDir, fileName)
  await writeFile(filePath, `${JSON.stringify(record, null, 2)}\n`, 'utf8')
  console.log(`[capture] ${req.method} ${req.url} -> ${filePath}`)
  return filePath
}

function sendJson(res, status, payload) {
  const text = JSON.stringify(payload)
  res.writeHead(status, {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(text),
  })
  res.end(text)
}

function sendStreamChatCompletion(res) {
  res.writeHead(200, {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  })
  const id = `chatcmpl-capture-${Date.now()}`
  const chunks = [
    { choices: [{ delta: { role: 'assistant', content: '' }, index: 0, finish_reason: null }] },
    { choices: [{ delta: { content: '{"captured":true}' }, index: 0, finish_reason: null }] },
    { choices: [{ delta: {}, index: 0, finish_reason: 'stop' }] },
  ]
  for (const chunk of chunks) {
    res.write(`data: ${JSON.stringify({ id, object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model: 'capture-model', ...chunk })}\n\n`)
  }
  res.end('data: [DONE]\n\n')
}

function sendChatCompletion(res) {
  sendJson(res, 200, {
    id: `chatcmpl-capture-${Date.now()}`,
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    model: 'capture-model',
    choices: [
      {
        index: 0,
        finish_reason: 'stop',
        message: {
          role: 'assistant',
          content: '{"captured":true}',
        },
      },
    ],
    usage: {
      prompt_tokens: 1,
      completion_tokens: 1,
      total_tokens: 2,
    },
  })
}

function sendResponsesResult(res, stream) {
  if (stream) {
    res.writeHead(200, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    })
    res.write(`data: ${JSON.stringify({ type: 'response.output_text.delta', delta: '{"captured":true}' })}\n\n`)
    res.end(`data: ${JSON.stringify({ type: 'response.completed', response: { id: `resp-capture-${Date.now()}`, status: 'completed' } })}\n\n`)
    return
  }
  sendJson(res, 200, {
    id: `resp-capture-${Date.now()}`,
    object: 'response',
    created_at: Math.floor(Date.now() / 1000),
    status: 'completed',
    model: 'capture-model',
    output_text: '{"captured":true}',
    output: [
      {
        type: 'message',
        role: 'assistant',
        content: [{ type: 'output_text', text: '{"captured":true}' }],
      },
    ],
  })
}

function parseJson(text) {
  try {
    return JSON.parse(text || '{}')
  } catch {
    return {}
  }
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'OPTIONS') {
      sendJson(res, 204, {})
      return
    }

    const url = new URL(req.url || '/', `http://${req.headers.host || `${host}:${port}`}`)
    if (req.method === 'GET' && (url.pathname === '/v1/models' || url.pathname === '/models')) {
      await capture(req, '')
      sendJson(res, 200, {
        object: 'list',
        data: [
          {
            id: 'capture-model',
            object: 'model',
            created: 1700000000,
            owned_by: 'local-capture',
            supported_endpoint_types: ['openai'],
          },
        ],
      })
      return
    }

    if (req.method === 'POST' && ['/v1/chat/completions', '/chat/completions'].includes(url.pathname)) {
      const bodyText = await readBody(req)
      await capture(req, bodyText)
      const body = parseJson(bodyText)
      if (body.stream === true) {
        sendStreamChatCompletion(res)
      } else {
        sendChatCompletion(res)
      }
      return
    }

    if (req.method === 'POST' && ['/v1/responses', '/responses'].includes(url.pathname)) {
      const bodyText = await readBody(req)
      await capture(req, bodyText)
      const body = parseJson(bodyText)
      sendResponsesResult(res, body.stream === true)
      return
    }

    if (req.method === 'POST' && ['/v1/completions', '/completions'].includes(url.pathname)) {
      const bodyText = await readBody(req)
      await capture(req, bodyText)
      sendJson(res, 200, {
        id: `cmpl-capture-${Date.now()}`,
        object: 'text_completion',
        created: Math.floor(Date.now() / 1000),
        model: 'capture-model',
        choices: [{ index: 0, text: '{"captured":true}', finish_reason: 'stop' }],
      })
      return
    }

    const bodyText = req.method === 'POST' || req.method === 'PUT' || req.method === 'PATCH'
      ? await readBody(req)
      : ''
    await capture(req, bodyText)
    sendJson(res, 404, {
      error: {
        type: 'capture_not_found',
        message: `Captured request, but no fake response is configured for ${req.method} ${url.pathname}`,
      },
    })
  } catch (error) {
    console.error('[capture] failed:', error)
    sendJson(res, 500, {
      error: {
        type: 'capture_server_error',
        message: error instanceof Error ? error.message : String(error),
      },
    })
  }
})

server.listen(port, host, () => {
  console.log(`OpenAI capture server listening at http://${host}:${port}/v1`)
  console.log(`Model: capture-model`)
  console.log(`Logs: ${logDir}`)
  console.log('Use any API key, for example: sk-capture')
})
