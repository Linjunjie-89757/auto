import { expect, test, type Page } from '@playwright/test'

type ApiSession = {
  cookie: string
}

type ApiEnvelope<T> = {
  success: boolean
  data: T
  message: string
}

type ApiDefinitionDetail = {
  id: number
}

const backendPort = Number(process.env.PLAYWRIGHT_BACKEND_PORT ?? '8081')
const apiBase = `http://127.0.0.1:${backendPort}/api`
const adminUser = { username: 'zhangli', password: '123456' }
const workspaceCode = 'risk-ops'
const definitionName = `api-fast-extract-${Date.now()}`

test.describe.serial('API fast extraction', () => {
  let api: ApiSession
  let definitionId: number | null = null

  test.beforeAll(async () => {
    api = await createApiContext()
    const definition = await apiPost<ApiDefinitionDetail>(api, '/automation/api/definitions', {
      workspaceCode,
      name: definitionName,
      directoryName: null,
      description: 'API fast extraction smoke',
      tags: ['fast-extraction'],
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
          id: 'fast-assert-body',
          assertionType: 'RESPONSE_BODY',
          name: 'Fast JSON Assertion',
          enabled: true,
          assertionBodyType: 'JSON_PATH',
          jsonPathAssertion: {
            responseFormat: 'XML',
            assertions: [
              { expression: '$.missingAssertion', condition: 'EQUALS', expectedValue: '', enabled: true },
            ],
          },
        },
      ],
      extractors: [],
      preProcessors: [],
      postProcessors: [
        {
          id: 'fast-post-extract',
          processorType: 'EXTRACT',
          name: 'Fast JSON Extract',
          enabled: true,
          extractors: [
            {
              variableName: 'userNameVar',
              description: 'fast extraction target',
              variableType: 'TEMPORARY',
              extractType: 'JSON_PATH',
              extractScope: 'BODY',
              expression: '$.missingExtractor',
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
  })

  test.afterAll(async () => {
    if (definitionId !== null) {
      await apiDelete(api, `/automation/api/definitions/${definitionId}`, false)
    }
  })

  test('post extractor and body assertion can use fast extraction drawer', async ({ page }) => {
    await login(page)
    await openDefinition(page, definitionName)

    await page.getByRole('button', { name: '发送', exact: true }).click()
    await expect(page.locator('.ms-like-response-tabs')).toBeVisible()

    await page.locator('[data-testid="request-tab-post"]').click()
    const processorRow = page.locator('[data-testid="processor-extract-row-post-0"]')
    await expect(processorRow).toBeVisible()
    await expect(processorRow.locator('.fast-extraction-dom-trigger')).toBeVisible()
    await processorRow.locator('.fast-extraction-dom-trigger').click()

    const drawer = page.locator('.el-drawer')
    await expect(drawer.getByText('快速提取')).toBeVisible()
    await drawer.locator('.fast-extraction-tree-label', { hasText: 'data' }).click()
    await drawer.locator('.fast-extraction-tree-label', { hasText: 'username' }).click()
    await expect(drawer.locator('.el-input input')).toHaveValue('$.data.username')
    await drawer.getByRole('button', { name: '测试' }).click()
    await drawer.getByRole('button', { name: '确认' }).click()
    await expect(processorRow.locator(':scope > .el-input input').first()).toHaveValue('$.data.username')

    await page.locator('[data-testid="request-tab-tests"]').click()
    await page.locator('.assertion-list-item', { hasText: 'Fast JSON Assertion' }).click()
    const assertionRow = page.locator('[data-testid="assertion-body-row-0"]')
    await expect(assertionRow.locator('.fast-extraction-dom-trigger')).toBeVisible()
    await assertionRow.locator('.fast-extraction-dom-trigger').click()
    await expect(drawer.getByText('快速提取')).toBeVisible()
    await drawer.locator('.fast-extraction-tree-label', { hasText: 'data' }).click()
    await drawer.locator('.fast-extraction-tree-label', { hasText: 'displayName' }).click()
    await expect(drawer.locator('.el-input input')).toHaveValue('$.data.displayName')
    await drawer.getByRole('button', { name: '测试' }).click()
    await drawer.getByRole('button', { name: '确认' }).click()

    await expect(page.locator('[data-testid="assertion-body-expression-0"]')).toHaveValue('$.data.displayName')
    await expect(page.locator('[data-testid="assertion-body-expected-0"]')).not.toHaveValue('')
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
