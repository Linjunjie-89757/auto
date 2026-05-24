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

const backendPort = Number(process.env.PLAYWRIGHT_BACKEND_PORT ?? '8080')
const apiBase = `http://127.0.0.1:${backendPort}/api`
const adminUser = { username: 'zhangli', password: '123456' }
const workspaceCode = 'risk-ops'
const definitionName = `api-case-drawer-layout-${Date.now()}`

test.describe.serial('API case drawer layout debug', () => {
  let api: ApiSession
  let definitionId: number | null = null

  test.beforeAll(async () => {
    api = await createApiContext()
    const definition = await apiPost<ApiDefinitionDetail>(api, '/automation/api/definitions', {
      workspaceCode,
      name: definitionName,
      directoryName: null,
      description: 'verify case drawer layout',
      tags: ['case-drawer-layout'],
      requestConfig: {
        method: 'POST',
        path: `${apiBase}/auth/me`,
        timeoutMs: 5000,
        queryParams: [],
        headers: [],
        cookies: [],
        body: {
          type: 'RAW_JSON',
          rawText: '{"account":"A241002","user_code":"A241002_jqql"}',
          jsonText: '{"account":"A241002","user_code":"A241002_jqql"}',
          formItems: [],
          contentType: 'application/json',
          fileName: '',
          binaryBase64: '',
        },
        authConfig: {
          authType: 'NONE',
          basicAuth: { userName: '', password: '' },
          digestAuth: { userName: '', password: '' },
        },
      },
      assertions: [],
      extractors: [],
      preProcessors: [],
      postProcessors: [],
    })
    definitionId = definition.id
  })

  test.afterAll(async () => {
    if (definitionId !== null) {
      await apiDelete(api, `/automation/api/definitions/${definitionId}`, false)
    }
  })

  test('log request/response layout metrics', async ({ page }) => {
    await login(page)
    await openDefinition(page, definitionName)

    await page.getByRole('button', { name: '用例', exact: true }).click()
    await page.getByRole('button', { name: '新建用例', exact: true }).click()

    const drawer = page.locator('.api-case-drawer:visible')
    await expect(drawer).toBeVisible()
    await drawer.getByRole('button', { name: '发送', exact: true }).click()
    await page.waitForTimeout(1200)

    const metrics = await page.evaluate(() => {
      const pick = (selector: string) => {
        const el = document.querySelector(selector) as HTMLElement | null
        if (!el) {
          return null
        }
        const rect = el.getBoundingClientRect()
        const style = getComputedStyle(el)
        return {
          selector,
          top: rect.top,
          bottom: rect.bottom,
          height: rect.height,
          display: style.display,
          position: style.position,
          overflow: style.overflow,
          minHeight: style.minHeight,
        }
      }

      return {
        requestBody: pick('.api-case-drawer .ms-like-request-body'),
        bodyModeShell: pick('.api-case-drawer .ms-like-body-mode-shell'),
        bodyEditor: pick('.api-case-drawer .case-drawer-body-editor'),
        bodyEditorInner: pick('.api-case-drawer .case-drawer-body-editor .ms-monaco-editor__body'),
        responseShell: pick('.api-case-drawer .case-drawer-response-shell'),
      }
    })

    console.log(JSON.stringify(metrics, null, 2))
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
