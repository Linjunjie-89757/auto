import { readdir, readFile, stat } from 'node:fs/promises'
import path from 'node:path'

const projectRoot = path.resolve(import.meta.dirname, '..')
const selfPath = path.resolve(import.meta.filename)
const includeExtensions = new Set([
  '.vue',
  '.ts',
  '.js',
  '.mjs',
  '.cjs',
  '.json',
  '.css',
  '.scss',
  '.html',
  '.md',
  '.yml',
  '.yaml',
])

const ignoreDirNames = new Set([
  'node_modules',
  'dist',
  'coverage',
  '.git',
  '.vite',
  '.idea',
  '.output',
])

const suspiciousPatterns = [
  { name: 'replacement-char', regex: /\uFFFD/ },
  { name: 'triple-question', regex: /\?{3,}/ },
  {
    name: 'common-mojibake',
    regex: /(鈥|锟|鍙傛|璇锋|鎻忚|鍒犻|璁よ瘉|鍓嶇疆|鍚庣疆|璁剧疆)/,
    ignoreSelf: true,
  },
]

async function walk(dirPath) {
  const entries = await readdir(dirPath, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const fullPath = path.join(dirPath, entry.name)
    if (entry.isDirectory()) {
      if (!ignoreDirNames.has(entry.name)) {
        files.push(...await walk(fullPath))
      }
      continue
    }
    if (!entry.isFile()) {
      continue
    }
    if (includeExtensions.has(path.extname(entry.name).toLowerCase())) {
      files.push(fullPath)
    }
  }
  return files
}

function getLineNumber(content, index) {
  let line = 1
  for (let i = 0; i < index; i += 1) {
    if (content[i] === '\n') {
      line += 1
    }
  }
  return line
}

function buildSnippet(content, index) {
  const start = Math.max(0, index - 16)
  const end = Math.min(content.length, index + 24)
  return content.slice(start, end).replace(/\r/g, '\\r').replace(/\n/g, '\\n')
}

async function main() {
  const files = await walk(path.join(projectRoot, 'src'))
  const extraPaths = [
    path.join(projectRoot, 'scripts'),
    path.join(projectRoot, 'package.json'),
    path.join(projectRoot, 'tsconfig.json'),
    path.join(projectRoot, 'vite.config.ts'),
  ]

  for (const extraPath of extraPaths) {
    try {
      const info = await stat(extraPath)
      if (info.isDirectory()) {
        files.push(...await walk(extraPath))
      }
      else if (info.isFile()) {
        files.push(extraPath)
      }
    }
    catch {
      // Optional path; ignore when absent.
    }
  }

  const issues = []
  for (const filePath of files) {
    const content = await readFile(filePath, 'utf8')
    for (const pattern of suspiciousPatterns) {
      if (pattern.ignoreSelf && path.resolve(filePath) === selfPath) {
        continue
      }
      pattern.regex.lastIndex = 0
      const match = pattern.regex.exec(content)
      if (!match || match.index == null) {
        continue
      }
      issues.push({
        filePath,
        line: getLineNumber(content, match.index),
        pattern: pattern.name,
        snippet: buildSnippet(content, match.index),
      })
    }
  }

  if (issues.length) {
    console.error('Encoding check failed. Suspicious text detected:')
    for (const issue of issues) {
      const relativePath = path.relative(projectRoot, issue.filePath)
      console.error(`- ${relativePath}:${issue.line} [${issue.pattern}] ${issue.snippet}`)
    }
    process.exitCode = 1
    return
  }

  console.log('Encoding check passed.')
}

await main()
