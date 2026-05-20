import { expect, test, type Locator, type Page } from '@playwright/test'

type ApiSession = {
  cookie: string
}

type ApiEnvelope<T> = {
  success: boolean
  data: T
  message: string
}

type PageResponse<T> = {
  items: T[]
  total: number
}

type DbConnectionItem = {
  id: number
  connectionName: string
}

type ApiDefinitionDetail = {
  id: number
}

const backendPort = Number(process.env.PLAYWRIGHT_BACKEND_PORT ?? '8081')
const apiBase = `http://127.0.0.1:${backendPort}/api`
const adminUser = { username: 'zhangli', password: '123456' }
const workspaceCode = 'risk-ops'
const workspaceName = 'Risk Ops'
const runToken = `${Date.now()}`
const uiDbName = `api-ui-db-${runToken}`
const uiDbNameUpdated = `${uiDbName}-updated`
const processorDbName = `api-ui-processor-db-${runToken}`
const definitionName = `api-ui-processors-${runToken}`

test.describe.serial('API UI smoke', () => {
  let api: ApiSession
  let uiDbConnectionId: number | null = null
  let processorDbConnectionId: number | null = null
  let definitionId: number | null = null

  test.beforeAll(async () => {
    api = await createApiContext()
  })

  test.afterAll(async () => {
    if (definitionId !== null) {
      await apiDelete(api, `/automation/api/definitions/${definitionId}`, false)
    }
    if (processorDbConnectionId !== null) {
      await apiDelete(api, `/settings/db-connections/${processorDbConnectionId}`, false)
    }
    if (uiDbConnectionId !== null) {
      await apiDelete(api, `/settings/db-connections/${uiDbConnectionId}`, false)
    }
  })

  test('database connection tab can create, edit and echo saved values', async ({ page }) => {
    await login(page)
    await page.goto('/settings?workspace=ALL')
    await expect(page.locator('main .page-title')).toHaveText('系统设置')

    await page.getByRole('tab', { name: '数据库连接' }).click()
    await expect(page.locator('[data-testid="db-connection-tab-pane"]')).toBeVisible()
    await page.locator('[data-testid="db-connection-create"]').click()

    const dialog = page.locator('[data-testid="db-connection-dialog"]')
    await expect(dialog).toBeVisible()
    await selectOption(dialog.locator('[data-testid="db-connection-workspace"]'), workspaceName)
    await fillInput(dialog.locator('[data-testid="db-connection-name"]'), uiDbName)
    await selectOption(dialog.locator('[data-testid="db-connection-type"]'), 'H2')
    await fillInput(dialog.locator('[data-testid="db-connection-driver"]'), 'org.h2.Driver')
    await fillInput(dialog.locator('[data-testid="db-connection-url"]'), `jdbc:h2:mem:${uiDbName};MODE=MySQL;DB_CLOSE_DELAY=-1`)
    await fillInput(dialog.locator('[data-testid="db-connection-username"]'), 'sa')
    await fillInput(dialog.locator('[data-testid="db-connection-password"]'), 'secret')
    await fillInput(dialog.locator('[data-testid="db-connection-description"]'), 'created from ui smoke')
    await dialog.getByRole('button', { name: '保存' }).click()

    const createdRow = findDataRow(page, uiDbName)
    await expect(createdRow).toContainText('H2')
    await expect(createdRow).toContainText('已配置')
    uiDbConnectionId = await findDbConnectionIdByName(api, uiDbName)

    await createdRow.getByRole('button', { name: '编辑' }).click()
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('[data-testid="db-connection-name"] input')).toHaveValue(uiDbName)
    await expect(dialog.locator('[data-testid="db-connection-url"] input')).toHaveValue(`jdbc:h2:mem:${uiDbName};MODE=MySQL;DB_CLOSE_DELAY=-1`)
    await expect(dialog.locator('[data-testid="db-connection-password"] input')).toHaveValue('')

    await fillInput(dialog.locator('[data-testid="db-connection-name"]'), uiDbNameUpdated)
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(findDataRow(page, uiDbNameUpdated)).toContainText('H2')
  })

  test('one-page API editor echoes SQL and extract processors after save and reload', async ({ page }) => {
    const dbConnection = await apiPost<DbConnectionItem>(api, '/settings/db-connections', {
      workspaceCode,
      connectionName: processorDbName,
      dbType: 'H2',
      driverClassName: 'org.h2.Driver',
      jdbcUrl: `jdbc:h2:mem:${processorDbName};MODE=MySQL;DB_CLOSE_DELAY=-1`,
      username: 'sa',
      password: '',
      poolMax: 2,
      timeoutMs: 5000,
      status: 1,
    })
    processorDbConnectionId = dbConnection.id

    const definition = await apiPost<ApiDefinitionDetail>(api, '/automation/api/definitions', {
      workspaceCode,
      name: definitionName,
      directoryName: null,
      description: 'API UI smoke',
      tags: ['ui-smoke'],
      requestConfig: {
        method: 'GET',
        path: `${apiBase}/auth/me`,
        timeoutMs: 5000,
        queryParams: [],
        headers: [],
        cookies: [],
        body: { type: 'NONE', rawText: null, formItems: [] },
        authConfig: {
          authType: 'NONE',
          basicAuth: { userName: '', password: '' },
          digestAuth: { userName: '', password: '' },
        },
      },
      assertions: [
        {
          id: 'ui-assert-status',
          assertionType: 'RESPONSE_CODE',
          name: 'UI Status Assertion',
          enabled: true,
          condition: 'EQUALS',
          expectedValue: '401',
        },
        {
          id: 'ui-assert-body',
          assertionType: 'RESPONSE_BODY',
          name: 'UI Body Regex',
          enabled: true,
          assertionBodyType: 'REGEX',
          regexAssertion: {
            responseFormat: 'XML',
            assertions: [
              { expression: '.*', condition: 'EQUALS', expectedValue: '', enabled: true },
            ],
          },
        },
        {
          id: 'ui-assert-variable',
          assertionType: 'VARIABLE',
          name: 'UI Variable Assertion',
          enabled: true,
          variableAssertionItems: [
            { variableName: 'firstToken', condition: 'EQUALS', expectedValue: 'ui-token', enabled: true },
          ],
        },
        {
          id: 'ui-assert-script',
          assertionType: 'SCRIPT',
          name: 'UI Script Assertion',
          enabled: true,
          scriptLanguage: 'JAVASCRIPT',
          script: "if (response.statusCode !== 401) fail('bad status')",
        },
      ],
      extractors: [],
      preProcessors: [
        {
          id: 'ui-pre-sql',
          processorType: 'SQL',
          name: 'UI Pre SQL',
          enabled: true,
          dataSourceId: dbConnection.id,
          dataSourceName: dbConnection.connectionName,
          queryTimeout: 5000,
          script: "SELECT 'ui-token' AS token",
          variableNames: 'token',
          extractParams: [{ key: 'firstToken', value: 'token', enabled: true }],
          resultVariable: 'sqlRows',
        },
      ],
      postProcessors: [
        {
          id: 'ui-post-extract',
          processorType: 'EXTRACT',
          name: 'UI Post Extract',
          enabled: true,
          extractors: [
            {
              variableName: 'statusCodeVar',
              description: 'status extractor',
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

    await login(page)
    await openDefinition(page, definitionName)

    await assertPreSqlProcessorEcho(page, 'sqlRows')
    await assertPostExtractProcessorEcho(page, 'status extractor')
    await assertAssertionsEcho(page)

    await page.locator('[data-testid="request-tab-pre"]').click()
    await fillInput(page.locator('[data-testid="processor-sql-result-variable-pre"]'), 'sqlRowsEcho')
    await page.locator('[data-testid="request-tab-post"]').click()
    await page.locator('[data-testid="processor-extract-row-post-0"]').locator(':scope > .el-input input').nth(1).fill('saved from ui')

    await Promise.all([
      page.waitForResponse(response => response.url().includes(`/api/automation/api/definitions/${definitionId}`) && response.request().method() === 'PUT'),
      page.getByRole('button', { name: '保存', exact: true }).click(),
    ])

    await page.reload()
    await openDefinition(page, definitionName)
    await assertPreSqlProcessorEcho(page, 'sqlRowsEcho')
    await assertPostExtractProcessorEcho(page, 'saved from ui')
    await assertAssertionsEcho(page)
  })
})

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(adminUser.username)
  await page.locator('input:not([readonly])').nth(1).fill(adminUser.password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard|\/settings|\/automation/)
}

async function openDefinition(page: Page, name: string) {
  await page.goto(`/automation/api?workspace=${workspaceCode}`)
  await expect(page.locator('.api-automation-page')).toBeVisible()
  await page.locator('.ms-like-sidebar-tools input').fill(name)
  for (let index = 0; index < 3 && await page.locator('.ms-like-tree-item-name', { hasText: name }).count() === 0; index++) {
    const collapsedNode = page.locator('.el-tree-node__expand-icon:not(.is-leaf):not(.expanded)').first()
    if (await collapsedNode.count() === 0) {
      break
    }
    await collapsedNode.click()
  }
  await expect(page.locator('.ms-like-tree-item-name', { hasText: name })).toBeVisible()
  await page.locator('.ms-like-tree-item-name', { hasText: name }).click()
  await expect(page.locator('.ms-like-editor-tab-label', { hasText: name })).toBeVisible()
}

async function assertPreSqlProcessorEcho(page: Page, resultVariable: string) {
  await page.locator('[data-testid="request-tab-pre"]').click()
  await expect(page.locator('[data-testid="pre-processors-section"]')).toBeVisible()
  await expect(page.locator('[data-testid="processor-name-pre"] input')).toHaveValue('UI Pre SQL')
  await expect(page.locator('[data-testid="processor-sql-datasource-pre"]').first()).toContainText(processorDbName)
  await expect(page.locator('[data-testid="processor-sql-variable-names-pre"] input')).toHaveValue('token')
  await expect(page.locator('[data-testid="processor-sql-result-variable-pre"] input')).toHaveValue(resultVariable)
  const extractRow = page.locator('[data-testid="processor-sql-extract-row-pre-0"]')
  await expect(extractRow.locator('input').nth(0)).toHaveValue('firstToken')
  await expect(extractRow.locator('input').nth(1)).toHaveValue('token')
}

async function assertPostExtractProcessorEcho(page: Page, description: string) {
  await page.locator('[data-testid="request-tab-post"]').click()
  await expect(page.locator('[data-testid="post-processors-section"]')).toBeVisible()
  await expect(page.locator('[data-testid="processor-name-post"] input')).toHaveValue('UI Post Extract')
  const extractRow = page.locator('[data-testid="processor-extract-row-post-0"]')
  await expect(extractRow.locator('.extractor-card-head .el-input input').first()).toHaveValue('statusCodeVar')
  await expect(extractRow.locator(':scope > .el-input input').nth(0)).toHaveValue('401')
  await expect(extractRow.locator(':scope > .el-input input').nth(1)).toHaveValue(description)
}

async function assertAssertionsEcho(page: Page) {
  await page.locator('[data-testid="request-tab-tests"]').click()
  await expect(page.locator('[data-testid="assertions-section"]')).toBeVisible()
  await expect(page.locator('[data-testid="assertion-name-input"]')).toHaveValue('UI Status Assertion')
  await expect(page.locator('[data-testid="assertion-code-expected"]')).toHaveValue('401')

  await page.locator('.assertion-list-item', { hasText: 'UI Body Regex' }).click()
  await expect(page.locator('[data-testid="assertion-body-type"]')).toContainText('Regex')
  await expect(page.locator('[data-testid="assertion-body-expression-0"]')).toHaveValue('.*')

  await page.locator('.assertion-list-item', { hasText: 'UI Variable Assertion' }).click()
  await expect(page.locator('[data-testid="assertion-variable-name-0"]')).toHaveValue('firstToken')
  await expect(page.locator('[data-testid="assertion-variable-expected-0"]')).toHaveValue('ui-token')

  await page.locator('.assertion-list-item', { hasText: 'UI Script Assertion' }).click()
  await expect(page.locator('[data-testid="assertion-name-input"]')).toHaveValue('UI Script Assertion')
}

async function fillInput(locator: Locator, value: string) {
  const input = locator.locator('input, textarea').first()
  await input.fill(value)
}

async function selectOption(locator: Locator, optionName: string | RegExp) {
  await locator.locator('.el-select__wrapper').click()
  await expect(locator.page().getByRole('option', { name: optionName })).toBeVisible()
  await locator.page().getByRole('option', { name: optionName }).click()
}

function findDataRow(page: Page, text: string): Locator {
  return page.locator('tr').filter({ hasText: text })
}

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

async function findDbConnectionIdByName(api: ApiSession, name: string) {
  const page = await apiGet<PageResponse<DbConnectionItem>>(api, '/settings/db-connections')
  const item = page.items.find(row => row.connectionName === name)
  expect(item, `DB connection not found: ${name}`).toBeTruthy()
  return item!.id
}

async function apiGet<T>(api: ApiSession, path: string) {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'GET',
    headers: requestHeaders(api),
  })
  const responseText = await response.text()
  expect(response.ok, `${path} failed with ${response.status}: ${responseText}`).toBeTruthy()
  const payload = JSON.parse(responseText) as ApiEnvelope<T>
  expect(payload.success).toBeTruthy()
  return payload.data
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
