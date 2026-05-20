import { expect, test } from '@playwright/test'

type ApiSession = {
  cookie: string
}

type ApiEnvelope<T> = {
  success: boolean
  data: T
  message: string
}

type DbConnectionItem = {
  id: number
}

type ApiDefinitionDetail = {
  id: number
}

type ApiRunResponse = {
  result: string
  stepResults: Array<{
    success: boolean
    assertionResults: Array<{ success: boolean; message: string }>
    extractionResults: Array<{ name: string; success: boolean; value: string | null }>
    processorResults: Array<{
      stage: 'PRE' | 'POST'
      processorType: string
      success: boolean
      outputVariables: Record<string, string>
    }>
  }>
}

const backendPort = Number(process.env.PLAYWRIGHT_BACKEND_PORT ?? '8081')
const apiBase = `http://127.0.0.1:${backendPort}/api`
const adminUser = { username: 'zhangli', password: '123456' }
const workspaceCode = 'risk-ops'
const runToken = `${Date.now()}`
const dbConnectionName = `api-sql-smoke-${runToken}`
const definitionName = `api-processor-smoke-${runToken}`

test.describe.serial('API processors smoke', () => {
  let api: ApiSession
  let dbConnectionId: number | null = null
  let definitionId: number | null = null

  test.beforeAll(async () => {
    api = await createApiContext()
  })

  test.afterAll(async () => {
    if (definitionId !== null) {
      await apiDelete(api, `/automation/api/definitions/${definitionId}`, false)
    }
    if (dbConnectionId !== null) {
      await apiDelete(api, `/settings/db-connections/${dbConnectionId}`, false)
    }
  })

  test('runs pre SQL and post extractor on one-page API definition data', async () => {
    const dbConnection = await apiPost<DbConnectionItem>(api, '/settings/db-connections', {
      workspaceCode,
      connectionName: dbConnectionName,
      dbType: 'H2',
      driverClassName: 'org.h2.Driver',
      jdbcUrl: `jdbc:h2:mem:${dbConnectionName};MODE=MySQL;DB_CLOSE_DELAY=-1`,
      username: 'sa',
      password: '',
      poolMax: 2,
      timeoutMs: 5000,
      status: 1,
    })
    dbConnectionId = dbConnection.id

    const definition = await apiPost<ApiDefinitionDetail>(api, '/automation/api/definitions', {
      workspaceCode,
      name: definitionName,
      directoryName: 'smoke',
      description: 'API processor smoke',
      tags: ['smoke'],
      requestConfig: {
        method: 'GET',
        path: `${apiBase}/auth/me`,
        timeoutMs: 5000,
        queryParams: [],
        headers: [
          { key: 'X-SQL-Smoke', value: '{{token_1}}', enabled: true },
        ],
        cookies: [],
        body: { type: 'NONE', rawText: null, formItems: [] },
        authConfig: {
          authType: 'NONE',
          basicAuth: { userName: '', password: '' },
          digestAuth: { userName: '', password: '' },
        },
      },
      assertions: [
        { type: 'STATUS_CODE', subject: '', operator: 'EQUALS', expectedValue: '401' },
      ],
      extractors: [],
      preProcessors: [
        {
          id: 'pre-sql-smoke',
          processorType: 'SQL',
          name: 'Pre SQL Smoke',
          enabled: true,
          dataSourceId: dbConnection.id,
          queryTimeout: 5000,
          script: "SELECT 'sql-smoke' AS token",
          variableNames: 'token',
          extractParams: [],
          resultVariable: 'sqlRows',
        },
      ],
      postProcessors: [
        {
          id: 'post-extract-smoke',
          processorType: 'EXTRACT',
          name: 'Post Extract Smoke',
          enabled: true,
          extractors: [
            {
              variableName: 'statusCodeVar',
              variableType: 'TEMPORARY',
              extractType: 'REGEX',
              extractScope: 'RESPONSE_CODE',
              expression: '401',
              expressionMatchingRule: 'EXPRESSION',
              resultMatchingRule: 'SPECIFIC',
              resultMatchingRuleNum: 1,
              responseFormat: 'JSON',
              enabled: true,
            },
          ],
        },
      ],
    })
    definitionId = definition.id

    const run = await apiPost<ApiRunResponse>(api, `/automation/api/definitions/${definition.id}/debug-run`, {
      workspaceCode,
      environmentId: null,
      variableSetId: null,
    })

    expect(run.result).toBe('SUCCESS')
    const step = run.stepResults[0]
    expect(step.success).toBeTruthy()
    expect(step.assertionResults.every(item => item.success)).toBeTruthy()
    expect(step.extractionResults).toContainEqual(expect.objectContaining({
      name: 'statusCodeVar',
      success: true,
      value: '401',
    }))
    expect(step.processorResults).toContainEqual(expect.objectContaining({
      stage: 'PRE',
      processorType: 'SQL',
      success: true,
      outputVariables: expect.objectContaining({ token_1: 'sql-smoke' }),
    }))
    expect(step.processorResults).toContainEqual(expect.objectContaining({
      stage: 'POST',
      processorType: 'EXTRACT',
      success: true,
      outputVariables: expect.objectContaining({ statusCodeVar: '401' }),
    }))
  })
})

async function createApiContext() {
  const response = await fetch(`${apiBase}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Workspace-Code': 'ALL',
    },
    body: JSON.stringify(adminUser),
  })
  expect(response.ok).toBeTruthy()
  const cookie = response.headers.get('set-cookie')
  expect(cookie).toBeTruthy()
  return { cookie: cookie! }
}

async function apiPost<T>(api: ApiSession, path: string, body: unknown) {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'POST',
    headers: requestHeaders(api),
    body: JSON.stringify(body),
  })
  const responseText = await response.text()
  expect(response.ok, `${path} failed with ${response.status}: ${responseText}`).toBeTruthy()
  const payload = JSON.parse(responseText) as ApiEnvelope<T>
  expect(payload.success).toBeTruthy()
  return payload.data
}

async function apiDelete(api: ApiSession, path: string, expectSuccess = true) {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'DELETE',
    headers: requestHeaders(api),
  })
  if (!expectSuccess && !response.ok) {
    return
  }
  const responseText = await response.text()
  expect(response.ok, `${path} failed with ${response.status}: ${responseText}`).toBeTruthy()
  const payload = JSON.parse(responseText) as ApiEnvelope<null>
  expect(payload.success).toBeTruthy()
}

function requestHeaders(api: ApiSession) {
  return {
    'Content-Type': 'application/json',
    Cookie: api.cookie,
    'X-Workspace-Code': workspaceCode,
  }
}
