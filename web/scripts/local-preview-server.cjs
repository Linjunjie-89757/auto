const http = require('http')
const fs = require('fs')
const path = require('path')

const rootDir = path.resolve(__dirname, '..', 'dist')
const apiTargetHost = '127.0.0.1'
const apiTargetPort = 8080
const port = Number(process.env.LOCAL_PREVIEW_PORT || 4173)

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

function sendFile(filePath, res) {
  fs.readFile(filePath, (error, buffer) => {
    if (error) {
      res.writeHead(404)
      res.end('Not Found')
      return
    }
    const ext = path.extname(filePath).toLowerCase()
    res.writeHead(200, { 'Content-Type': contentTypes[ext] || 'application/octet-stream' })
    res.end(buffer)
  })
}

function proxyApi(req, res) {
  const proxyReq = http.request(
    {
      host: apiTargetHost,
      port: apiTargetPort,
      path: req.url,
      method: req.method,
      headers: req.headers,
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
      proxyRes.pipe(res)
    },
  )

  proxyReq.on('error', (error) => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end(`API proxy error: ${error.message}`)
  })

  req.pipe(proxyReq)
}

const server = http.createServer((req, res) => {
  if (!req.url) {
    res.writeHead(400)
    res.end('Bad Request')
    return
  }

  if (req.url.startsWith('/api')) {
    proxyApi(req, res)
    return
  }

  const requestPath = req.url === '/' ? '/index.html' : req.url.split('?')[0]
  const decodedPath = decodeURIComponent(requestPath)
  let filePath = path.normalize(path.join(rootDir, decodedPath))

  if (!filePath.startsWith(rootDir)) {
    res.writeHead(403)
    res.end('Forbidden')
    return
  }

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(rootDir, 'index.html')
  }

  sendFile(filePath, res)
})

server.listen(port, '127.0.0.1', () => {
  console.log(`Local preview server running at http://127.0.0.1:${port}`)
})
