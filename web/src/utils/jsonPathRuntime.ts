type JsonPathKey = string | number

interface JsonPathNode {
  value: unknown
  path: string
  parent: unknown
  key: JsonPathKey | null
}

type JsonPathToken =
  | { type: 'prop', key: string }
  | { type: 'index', index: number }
  | { type: 'wildcard' }
  | { type: 'slice', start: number | null, end: number | null, step: number | null }
  | { type: 'union', selectors: JsonPathToken[] }
  | { type: 'filter', expression: string }
  | { type: 'script', expression: string }
  | { type: 'recursive', selector: JsonPathToken }

const IDENTIFIER_RE = /^[A-Za-z_$][\w$-]*$/

function hasOwn(value: unknown, key: JsonPathKey) {
  return (typeof value === 'object' || typeof value === 'function')
    && value !== null
    && Object.prototype.hasOwnProperty.call(value, key)
}

function buildPath(basePath: string, key: JsonPathKey) {
  if (typeof key === 'number') {
    return `${basePath}[${key}]`
  }
  if (IDENTIFIER_RE.test(key)) {
    return basePath === '$' ? `$.${key}` : `${basePath}.${key}`
  }
  return `${basePath}[${JSON.stringify(key)}]`
}

function normalizeIndex(index: number, length: number) {
  return index < 0 ? length + index : index
}

function splitTopLevel(source: string, delimiter: string) {
  const parts: string[] = []
  let current = ''
  let squareDepth = 0
  let roundDepth = 0
  let quote: '\'' | '"' | null = null
  let escaped = false

  for (let i = 0; i < source.length; i += 1) {
    const char = source[i]
    if (quote) {
      current += char
      if (escaped) {
        escaped = false
      }
      else if (char === '\\') {
        escaped = true
      }
      else if (char === quote) {
        quote = null
      }
      continue
    }

    if (char === '\'' || char === '"') {
      quote = char
      current += char
      continue
    }
    if (char === '[') {
      squareDepth += 1
      current += char
      continue
    }
    if (char === ']') {
      squareDepth -= 1
      current += char
      continue
    }
    if (char === '(') {
      roundDepth += 1
      current += char
      continue
    }
    if (char === ')') {
      roundDepth -= 1
      current += char
      continue
    }
    if (char === delimiter && squareDepth === 0 && roundDepth === 0) {
      parts.push(current.trim())
      current = ''
      continue
    }
    current += char
  }

  if (current.trim() || source.endsWith(delimiter)) {
    parts.push(current.trim())
  }
  return parts
}

function readQuotedValue(source: string) {
  try {
    return Function(`"use strict"; return (${source});`)()
  }
  catch (error) {
    const detail = error instanceof Error ? error.message : String(error)
    throw new Error(`Invalid JSONPath quoted selector: ${detail}`)
  }
}

function parseBracketToken(content: string): JsonPathToken {
  const inner = content.trim()
  if (!inner) {
    throw new Error('Invalid JSONPath: empty bracket selector')
  }
  if (inner === '*') {
    return { type: 'wildcard' }
  }
  if (inner.startsWith('?(') && inner.endsWith(')')) {
    return { type: 'filter', expression: inner.slice(2, -1).trim() }
  }
  if (inner.startsWith('(') && inner.endsWith(')')) {
    return { type: 'script', expression: inner.slice(1, -1).trim() }
  }

  const unionParts = splitTopLevel(inner, ',')
  if (unionParts.length > 1) {
    return {
      type: 'union',
      selectors: unionParts.map(part => parseBracketToken(part)),
    }
  }

  if (splitTopLevel(inner, ':').length > 1) {
    const [startPart = '', endPart = '', stepPart = ''] = splitTopLevel(inner, ':')
    return {
      type: 'slice',
      start: startPart === '' ? null : Number.parseInt(startPart, 10),
      end: endPart === '' ? null : Number.parseInt(endPart, 10),
      step: stepPart === '' ? null : Number.parseInt(stepPart, 10),
    }
  }

  if ((inner.startsWith('\'') && inner.endsWith('\'')) || (inner.startsWith('"') && inner.endsWith('"'))) {
    return { type: 'prop', key: `${readQuotedValue(inner)}` }
  }

  if (/^-?\d+$/.test(inner)) {
    return { type: 'index', index: Number.parseInt(inner, 10) }
  }

  return { type: 'prop', key: inner }
}

function readBracketContent(path: string, startIndex: number) {
  let index = startIndex + 1
  let depth = 1
  let quote: '\'' | '"' | null = null
  let escaped = false
  let content = ''

  while (index < path.length) {
    const char = path[index]
    if (quote) {
      content += char
      if (escaped) {
        escaped = false
      }
      else if (char === '\\') {
        escaped = true
      }
      else if (char === quote) {
        quote = null
      }
      index += 1
      continue
    }

    if (char === '\'' || char === '"') {
      quote = char
      content += char
      index += 1
      continue
    }
    if (char === '[') {
      depth += 1
      content += char
      index += 1
      continue
    }
    if (char === ']') {
      depth -= 1
      if (depth === 0) {
        return { content, endIndex: index + 1 }
      }
      content += char
      index += 1
      continue
    }
    content += char
    index += 1
  }

  throw new Error('Invalid JSONPath: missing closing "]"')
}

function readIdentifier(path: string, startIndex: number) {
  let index = startIndex
  while (index < path.length && /[A-Za-z0-9_$-]/.test(path[index])) {
    index += 1
  }
  if (index === startIndex) {
    throw new Error(`Invalid JSONPath near "${path.slice(startIndex)}"`)
  }
  return {
    value: path.slice(startIndex, index),
    endIndex: index,
  }
}

function parseRecursiveSelector(path: string, startIndex: number) {
  const char = path[startIndex]
  if (char === '*') {
    return { token: { type: 'wildcard' } as JsonPathToken, endIndex: startIndex + 1 }
  }
  if (char === '[') {
    const { content, endIndex } = readBracketContent(path, startIndex)
    return { token: parseBracketToken(content), endIndex }
  }
  const { value, endIndex } = readIdentifier(path, startIndex)
  return { token: { type: 'prop', key: value } as JsonPathToken, endIndex }
}

function parseJsonPath(path: string) {
  const normalized = path.trim()
  if (!normalized) {
    throw new Error('JSONPath expression is empty')
  }
  if (normalized === '$') {
    return [] as JsonPathToken[]
  }

  let index = normalized.startsWith('$') ? 1 : 0
  const tokens: JsonPathToken[] = []

  while (index < normalized.length) {
    const char = normalized[index]
    if (/\s/.test(char)) {
      index += 1
      continue
    }

    if (normalized.startsWith('..', index)) {
      const { token, endIndex } = parseRecursiveSelector(normalized, index + 2)
      tokens.push({ type: 'recursive', selector: token })
      index = endIndex
      continue
    }

    if (char === '.') {
      if (normalized[index + 1] === '*') {
        tokens.push({ type: 'wildcard' })
        index += 2
        continue
      }
      const { value, endIndex } = readIdentifier(normalized, index + 1)
      tokens.push({ type: 'prop', key: value })
      index = endIndex
      continue
    }

    if (char === '[') {
      const { content, endIndex } = readBracketContent(normalized, index)
      tokens.push(parseBracketToken(content))
      index = endIndex
      continue
    }

    const { value, endIndex } = readIdentifier(normalized, index)
    tokens.push({ type: 'prop', key: value })
    index = endIndex
  }

  return tokens
}

function transformJsonPathExpression(expression: string) {
  return expression
    .replaceAll('@parentProperty', '_parentProperty')
    .replaceAll('@parent', '_parent')
    .replaceAll('@property', '_property')
    .replaceAll('@root', '_root')
    .replaceAll('@path', '_path')
    .replace(/@(?=\.|\[|$|\s|[!=<>+\-*\/%?:),\]])/g, '_value')
}

function evaluateExpression(expression: string, node: JsonPathNode, root: unknown) {
  const script = transformJsonPathExpression(expression)
  try {
    return Function(
      '_value',
      '_root',
      '_parent',
      '_property',
      '_parentProperty',
      '_path',
      `"use strict"; return (${script});`,
    )(
      node.value,
      root,
      node.parent,
      node.key,
      node.key,
      node.path,
    )
  }
  catch (error) {
    const detail = error instanceof Error ? error.message : String(error)
    throw new Error(`Invalid JSONPath expression: ${detail}`)
  }
}

function dedupeNodes(nodes: JsonPathNode[]) {
  const seen = new Set<string>()
  return nodes.filter((node) => {
    if (seen.has(node.path)) {
      return false
    }
    seen.add(node.path)
    return true
  })
}

function getChildren(node: JsonPathNode) {
  if (Array.isArray(node.value)) {
    return node.value.map((item, index) => ({
      value: item,
      parent: node.value,
      key: index,
      path: buildPath(node.path, index),
    }))
  }
  if (node.value && typeof node.value === 'object') {
    return Object.keys(node.value as Record<string, unknown>).map((key) => ({
      value: (node.value as Record<string, unknown>)[key],
      parent: node.value,
      key,
      path: buildPath(node.path, key),
    }))
  }
  return [] as JsonPathNode[]
}

function walkDescendants(node: JsonPathNode) {
  const descendants: JsonPathNode[] = []
  const stack = [...getChildren(node)]
  while (stack.length) {
    const current = stack.shift() as JsonPathNode
    descendants.push(current)
    stack.unshift(...getChildren(current))
  }
  return descendants
}

function applySelector(node: JsonPathNode, token: JsonPathToken, root: unknown): JsonPathNode[] {
  if (token.type === 'prop') {
    if (!hasOwn(node.value, token.key)) {
      return []
    }
    return [{
      value: (node.value as Record<string, unknown>)[token.key],
      parent: node.value,
      key: token.key,
      path: buildPath(node.path, token.key),
    }]
  }

  if (token.type === 'index') {
    if (!Array.isArray(node.value)) {
      return []
    }
    const index = normalizeIndex(token.index, node.value.length)
    if (index < 0 || index >= node.value.length) {
      return []
    }
    return [{
      value: node.value[index],
      parent: node.value,
      key: index,
      path: buildPath(node.path, index),
    }]
  }

  if (token.type === 'wildcard') {
    return getChildren(node)
  }

  if (token.type === 'slice') {
    if (!Array.isArray(node.value)) {
      return []
    }
    const length = node.value.length
    const step = token.step ?? 1
    if (step === 0) {
      return []
    }
    const defaultStart = step > 0 ? 0 : length - 1
    const defaultEnd = step > 0 ? length : -1
    let start = token.start ?? defaultStart
    let end = token.end ?? defaultEnd
    start = normalizeIndex(start, length)
    end = end < 0 ? length + end : end

    const results: JsonPathNode[] = []
    if (step > 0) {
      start = Math.max(0, start)
      end = Math.min(length, end)
      for (let i = start; i < end; i += step) {
        results.push({
          value: node.value[i],
          parent: node.value,
          key: i,
          path: buildPath(node.path, i),
        })
      }
    }
    else {
      start = Math.min(length - 1, start)
      end = Math.max(-1, end)
      for (let i = start; i > end; i += step) {
        results.push({
          value: node.value[i],
          parent: node.value,
          key: i,
          path: buildPath(node.path, i),
        })
      }
    }
    return results
  }

  if (token.type === 'union') {
    return dedupeNodes(token.selectors.flatMap(selector => applySelector(node, selector, root)))
  }

  if (token.type === 'filter') {
    return getChildren(node).filter(child => !!evaluateExpression(token.expression, child, root))
  }

  if (token.type === 'script') {
    const result = evaluateExpression(token.expression, node, root)
    const selectors = Array.isArray(result) ? result : [result]
    return dedupeNodes(selectors.flatMap((selector) => {
      if (typeof selector === 'number' && Number.isInteger(selector)) {
        return applySelector(node, { type: 'index', index: selector }, root)
      }
      if (typeof selector === 'string') {
        return applySelector(node, { type: 'prop', key: selector }, root)
      }
      return []
    }))
  }

  const scope = [node, ...walkDescendants(node)]
  return dedupeNodes(scope.flatMap(candidate => applySelector(candidate, token.selector, root)))
}

export function evaluateJsonPath(root: unknown, expression: string) {
  const tokens = parseJsonPath(expression)
  let nodes: JsonPathNode[] = [{
    value: root,
    path: '$',
    parent: null,
    key: null,
  }]

  for (const token of tokens) {
    nodes = dedupeNodes(nodes.flatMap(node => applySelector(node, token, root)))
  }

  return nodes.map(node => node.value)
}
