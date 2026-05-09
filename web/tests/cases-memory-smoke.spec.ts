import { expect, test, type Page } from '@playwright/test'

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
  pageNo: number
  pageSize: number
  totalPages: number
}

type CaseSummary = {
  id: number
  title: string
}

type WorkspaceItem = {
  code: string
  name: string
}

const adminUser = { username: 'zhangli', password: '123456' }
const workspaceCode = 'account-open'
const casePrefix = `MEMORY-CASE-${Date.now()}`

test.describe.serial('Cases tab memory smoke', () => {
  let api: ApiSession
  let workspaceName = workspaceCode

  test.beforeAll(async () => {
    api = await createApiContext()
    workspaceName = await loadWorkspaceName(api, workspaceCode)
    await cleanupCases(api)
    await seedCases(api, 12)
  })

  test.afterAll(async () => {
    await cleanupCases(api)
  })

  test('AI generate draft survives tab switching', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto(`/cases/ai-generate?workspace=${workspaceCode}`)

    await expect(page.locator('main .page-title')).toContainText('AI')
    await page.locator('input[placeholder*="例如"]').fill('记忆验证标题')
    await page.locator('textarea').first().fill('这里是一段用于验证 AI 生成页切换记忆的需求内容。')
    await page.locator('.el-input-number input').fill('15')

    await page.locator('.case-tabs .el-tabs__item').nth(0).click()
    await expect(page).toHaveURL(/\/cases\/manage/)
    await page.locator('.case-tabs .el-tabs__item').nth(1).click()
    await expect(page).toHaveURL(/\/cases\/ai-generate/)

    await expect(page.locator('input[placeholder*="例如"]')).toHaveValue('记忆验证标题')
    await expect(page.locator('textarea').first()).toHaveValue('这里是一段用于验证 AI 生成页切换记忆的需求内容。')
    await expect(page.locator('.el-input-number input')).toHaveValue('15')
  })

  test('case tree selection and current page survive tab switching', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/cases/manage?workspace=ALL')

    await expect(page.locator('main .page-title')).toContainText('用例')
    await page.evaluate(() => {
      const raw = localStorage.getItem('case-table-settings-v2')
      const parsed = raw ? JSON.parse(raw) : {}
      parsed.pageSize = 10
      localStorage.setItem('case-table-settings-v2', JSON.stringify(parsed))
    })
    await page.reload()

    await page.locator('.case-tree .tree-node-label', { hasText: workspaceName }).first().click()
    const currentTreeNodeLabel = page.locator('.case-tree .el-tree-node.is-current > .el-tree-node__content .tree-node-label')
    await expect(currentTreeNodeLabel).toContainText(workspaceName)

    const workspaceTreeNode = page.locator('.case-tree .el-tree-node', {
      has: page.locator('> .el-tree-node__content .tree-node-label', { hasText: workspaceName }),
    }).first()
    const workspaceExpandIcon = workspaceTreeNode.locator('> .el-tree-node__content .el-tree-node__expand-icon').first()
    if (await workspaceExpandIcon.evaluate((element) => !element.classList.contains('expanded'))) {
      await workspaceExpandIcon.click()
    }
    await expect(workspaceTreeNode).toHaveClass(/is-expanded/)

    const pagerTwo = page.locator('.table-pagination-right .el-pager li').filter({ hasText: '2' }).first()
    await expect(pagerTwo).toBeVisible()
    await pagerTwo.click()
    await expect(page.locator('.table-pagination-right .el-pager li.is-active')).toHaveText('2')

    await page.locator('.case-tabs .el-tabs__item').nth(1).click()
    await expect(page).toHaveURL(/\/cases\/ai-generate/)
    await page.locator('.case-tabs .el-tabs__item').nth(0).click()
    await expect(page).toHaveURL(/\/cases\/manage/)

    await expect(currentTreeNodeLabel).toContainText(workspaceName)
    await expect(page.locator('.table-pagination-right .el-pager li.is-active')).toHaveText('2')
    await expect(workspaceTreeNode).toHaveClass(/is-expanded/)
  })
})

async function createApiContext() {
  const response = await fetch('http://127.0.0.1:8080/api/auth/login', {
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

async function loadWorkspaceName(api: ApiSession, targetWorkspaceCode: string) {
  const items = await apiGet<WorkspaceItem[]>(api, '/workspaces/switchable')
  return items.find(item => item.code === targetWorkspaceCode)?.name ?? targetWorkspaceCode
}

async function cleanupCases(api: ApiSession) {
  const casePage = await apiGet<PageResponse<CaseSummary>>(api, `/cases?pageNo=1&pageSize=200&workspaceCode=${workspaceCode}`)
  for (const item of casePage.items.filter(row => row.title.startsWith(casePrefix))) {
    await apiDelete(api, `/cases/${item.id}`)
  }
}

async function seedCases(api: ApiSession, count: number) {
  for (let index = 1; index <= count; index += 1) {
    await apiPost(api, '/cases', {
      title: `${casePrefix}-${String(index).padStart(2, '0')}`,
      caseType: 'FUNCTION',
      priority: 'P1',
      sourceType: '手工创建',
      caseStatus: '草稿',
      ownerId: null,
      precondition: `seed-precondition-${index}`,
      steps: `seed-steps-${index}`,
      expectedResult: `seed-expected-${index}`,
    }, workspaceCode)
  }
}

async function apiGet<T>(api: ApiSession, path: string) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': 'ALL',
    },
  })
  expect(response.ok).toBeTruthy()
  const payload = await response.json() as ApiEnvelope<T>
  expect(payload.success).toBeTruthy()
  return payload.data
}

async function apiPost<T>(api: ApiSession, path: string, body: unknown, requestWorkspaceCode: string) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': requestWorkspaceCode,
    },
    body: JSON.stringify(body),
  })
  expect(response.ok).toBeTruthy()
  const payload = await response.json() as ApiEnvelope<T>
  expect(payload.success).toBeTruthy()
  return payload.data
}

async function apiDelete(api: ApiSession, path: string) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': workspaceCode,
    },
  })
  expect(response.ok).toBeTruthy()
  const payload = await response.json() as ApiEnvelope<null>
  expect(payload.success).toBeTruthy()
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(username)
  await page.locator('input:not([readonly])').nth(1).fill(password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/)
}
