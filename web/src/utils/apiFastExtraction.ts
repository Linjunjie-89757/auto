export type FastExtractionMode = 'JSON_PATH' | 'X_PATH' | 'REGEX'
export type FastExtractionExpressionRule = 'EXPRESSION' | 'GROUP'
export type FastExtractionResponseFormat = 'JSON' | 'XML' | 'HTML'

export interface FastExtractionConfig {
  expression?: string
  extractType?: FastExtractionMode
  expressionMatchingRule?: FastExtractionExpressionRule
  responseFormat?: FastExtractionResponseFormat
}

function stringifyMatchValue(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  return JSON.stringify(value)
}

function tokenizeSimpleJsonPath(expression: string): Array<string | number> | null {
  const trimmed = expression.trim()
  if (!trimmed || trimmed === '$') {
    return []
  }
  let path = trimmed
  if (path.startsWith('$')) {
    path = path.slice(1)
  }

  const tokens: Array<string | number> = []
  let index = 0

  while (index < path.length) {
    const char = path[index]
    if (char === '.') {
      index += 1
      continue
    }
    if (char === '[') {
      const nextChar = path[index + 1]
      if (nextChar === '"' || nextChar === '\'') {
        const quote = nextChar
        index += 2
        let buffer = ''
        while (index < path.length) {
          const current = path[index]
          if (current === '\\' && index + 1 < path.length) {
            buffer += path[index + 1]
            index += 2
            continue
          }
          if (current === quote) {
            break
          }
          buffer += current
          index += 1
        }
        if (path[index] !== quote) {
          return null
        }
        index += 1
        if (path[index] !== ']') {
          return null
        }
        tokens.push(buffer)
        index += 1
        continue
      }

      const endIndex = path.indexOf(']', index)
      if (endIndex < 0) {
        return null
      }
      const segment = path.slice(index + 1, endIndex).trim()
      if (/^\d+$/.test(segment)) {
        tokens.push(Number(segment))
      }
      else if (segment) {
        tokens.push(segment)
      }
      else {
        return null
      }
      index = endIndex + 1
      continue
    }

    let endIndex = index
    while (endIndex < path.length && /[\w$-]/.test(path[endIndex])) {
      endIndex += 1
    }
    if (endIndex === index) {
      return null
    }
    tokens.push(path.slice(index, endIndex))
    index = endIndex
  }

  return tokens
}

export function readSimpleJsonPath(value: unknown, expression: string): string {
  const tokens = tokenizeSimpleJsonPath(expression)
  if (!tokens) {
    return ''
  }

  let current: any = value
  for (const token of tokens) {
    current = current?.[token as any]
  }
  return stringifyMatchValue(current)
}

export function matchRegex(
  source: string,
  expression: string,
  rule: FastExtractionExpressionRule = 'EXPRESSION',
): string[] {
  const normalized = expression.trim().replace(/^\/|\/$|\/g$/g, '')
  if (!normalized) {
    return []
  }
  const matches = [...source.matchAll(new RegExp(normalized, 'gs'))]
  if (rule === 'GROUP') {
    return matches.flatMap(match => match.slice(1).filter(item => item !== undefined).map(item => `${item}`))
  }
  return matches.map(match => match[0])
}

export function matchXPath(
  source: string,
  expression: string,
  responseFormat: FastExtractionResponseFormat = 'XML',
): string[] {
  if (!expression.trim()) {
    return []
  }

  const parserType = responseFormat === 'HTML' ? 'text/html' : 'text/xml'
  const doc = new DOMParser().parseFromString(source || '', parserType)
  const result = doc.evaluate(expression, doc, null, XPathResult.ANY_TYPE, null)
  const values: string[] = []

  let node = result.iterateNext()
  while (node) {
    values.push(node.textContent ?? '')
    node = result.iterateNext()
  }

  if (values.length) {
    return values
  }

  const scalar = doc.evaluate(expression, doc, null, XPathResult.STRING_TYPE, null).stringValue
  return scalar ? [scalar] : []
}

export function matchJsonPath(source: string, expression: string): string[] {
  const parsed = JSON.parse(source || '{}')
  const value = readSimpleJsonPath(parsed, expression)
  return value ? [value] : []
}

export function testFastExtraction(source: string, config: FastExtractionConfig): string[] {
  const mode = config.extractType || 'JSON_PATH'
  const expression = config.expression || ''
  if (mode === 'REGEX') {
    return matchRegex(source, expression, config.expressionMatchingRule || 'EXPRESSION')
  }
  if (mode === 'X_PATH') {
    return matchXPath(source, expression, config.responseFormat || 'XML')
  }
  return matchJsonPath(source, expression)
}

export function buildJsonPathSegment(basePath: string, key: string | number): string {
  if (typeof key === 'number') {
    return `${basePath}[${key}]`
  }
  if (/^[A-Za-z_$][\w$-]*$/.test(key)) {
    return basePath === '$' ? `$.${key}` : `${basePath}.${key}`
  }
  return `${basePath}[${JSON.stringify(key)}]`
}

export function buildXPathSegment(basePath: string, nodeName: string, index?: number): string {
  const segment = index && index > 1 ? `${nodeName}[${index}]` : nodeName
  if (!basePath || basePath === '/') {
    return `/${segment}`
  }
  return `${basePath}/${segment}`
}
