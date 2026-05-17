import path from 'node:path'

const originalRelative = path.relative.bind(path)

function stripWindowsDrivePrefix(value) {
  return value.replace(/^[\\/](?=[A-Za-z]:[\\/])/, '')
}

path.relative = function patchedRelative(from, to) {
  let result = originalRelative(from, to)

  if (process.platform !== 'win32') {
    return result
  }

  if (/^[\\/][A-Za-z]:[\\/]/.test(result)) {
    result = originalRelative(stripWindowsDrivePrefix(from), stripWindowsDrivePrefix(to))
  }

  return stripWindowsDrivePrefix(result)
}

const { build } = await import('vite')

await build()
