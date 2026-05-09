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
  pageNo: number
  pageSize: number
  totalPages: number
}

type CaseItem = {
  id: number
  title: string
}

const adminUser = { username: 'zhangli', password: '123456' }
const casePrefix = `SMOKE-CASE-${Date.now()}`
const scopedCaseTitle = `${casePrefix}-SCOPED`
const scopedCaseTitleUpdated = `${scopedCaseTitle}-UPDATED`
const allCaseTitle = `${casePrefix}-ALL`

test.describe.serial('Cases CRUD smoke', () => {
  let api: ApiSession

  test.beforeAll(async () => {
    api = await createApiContext()
    await cleanupCases(api)
  })

  test.afterAll(async () => {
    await cleanupCases(api)
  })

  test('admin can create, view, edit and delete a case in scoped workspace', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/cases/manage?workspace=account-open')
    await expect(page.locator('main .page-title')).toBeVisible()

    await page.locator('.case-create-button').click()
    let drawer = visibleDrawer(page)
    await drawer.locator('input:not([readonly])').first().fill(scopedCaseTitle)
    await drawer.locator('textarea').nth(0).fill('Smoke precondition')
    await drawer.locator('textarea').nth(1).fill('Smoke steps')
    await drawer.locator('textarea').nth(2).fill('Smoke expected result')
    await drawer.getByRole('button', { name: '保存' }).last().click()

    let row = findDataRow(page, scopedCaseTitle)
    await expect(row).toBeVisible()

    await findActionRow(page, scopedCaseTitle).getByRole('button', { name: '编辑' }).click()
    drawer = visibleDrawer(page)
    await drawer.locator('input:not([readonly])').first().fill(scopedCaseTitleUpdated)
    await drawer.locator('textarea').nth(0).fill('Smoke precondition updated')
    await drawer.getByRole('button', { name: '保存' }).last().click()

    row = findDataRow(page, scopedCaseTitleUpdated)
    await expect(row).toBeVisible()

    await openDeleteFromRow(page, findActionRow(page, scopedCaseTitleUpdated))
    await confirmDangerDialog(page)
    await expect(findDataRow(page, scopedCaseTitleUpdated)).toHaveCount(0)
  })

  test('ALL scope requires selecting a concrete workspace before creating a case', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/cases/manage?workspace=ALL')
    await expect(page.locator('main .page-title')).toBeVisible()

    await page.locator('.case-create-button').click()
    await expect(visibleDrawer(page)).toHaveCount(0)
    await expect(findDataRow(page, allCaseTitle)).toHaveCount(0)

    await page.locator('.case-tree .tree-node-label', { hasText: 'XMAN' }).first().click()
    await page.locator('.case-create-button').click()
    const drawer = visibleDrawer(page)
    await drawer.locator('input:not([readonly])').first().fill(allCaseTitle)
    await drawer.getByRole('button', { name: '保存' }).last().click()

    const row = findDataRow(page, allCaseTitle)
    await expect(row).toBeVisible()
    await openDeleteFromRow(page, findActionRow(page, allCaseTitle))
    await confirmDangerDialog(page)
    await expect(findDataRow(page, allCaseTitle)).toHaveCount(0)
  })

  test('table settings can change page size, visible columns and column order', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/cases/manage?workspace=ALL')
    await expect(page.locator('main .page-title')).toBeVisible()

    const settingsButton = page.locator('.case-actions-header .table-settings-trigger')
    await settingsButton.click()

    const drawer = visibleDrawer(page)
    const pageSizeOptions = drawer.locator('.page-size-option')
    await expect(pageSizeOptions.nth(1)).toBeVisible()
    await pageSizeOptions.nth(1).click()
    await expect.poll(async () => {
      return page.evaluate(() => {
        const raw = localStorage.getItem('case-table-settings-v2')
        return raw ? JSON.parse(raw).pageSize : null
      })
    }).toBe(20)

    const createdByRow = drawer.locator('.settings-item[data-column-key="createdByName"]')
    const createdBySwitch = createdByRow.locator('.el-switch')
    await createdBySwitch.click()
    await expect(page.locator('.case-grid-header [data-column-key="createdByName"]')).toBeVisible()

    const executionRow = drawer.locator('.settings-item[data-column-key="executionStatus"]')
    const directoryRow = drawer.locator('.settings-item[data-column-key="directoryName"]')
    await directoryRow.dragTo(executionRow)
    await expect.poll(async () => {
      return page.evaluate(() => {
        const raw = localStorage.getItem('case-table-settings-v2')
        const order = raw ? JSON.parse(raw).columnOrder as string[] : []
        return order.indexOf('directoryName') !== -1
          && order.indexOf('executionStatus') !== -1
          && order.indexOf('directoryName') < order.indexOf('executionStatus')
      })
    }).toBeTruthy()
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

async function cleanupCases(api: ApiSession) {
  const casePage = await apiGet<PageResponse<CaseItem>>(api, '/cases?pageNo=1&pageSize=200')
  for (const item of casePage.items.filter(row => row.title.startsWith(casePrefix))) {
    await apiDelete(api, `/cases/${item.id}`)
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

async function apiDelete(api: ApiSession, path: string) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': 'ALL',
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

function visibleDrawer(page: Page): Locator {
  return page.locator('.el-drawer:visible').last()
}

async function confirmDangerDialog(page: Page) {
  const dialog = page.getByRole('dialog').last()
  await dialog.getByRole('button', { name: /确定|确认|OK/ }).click()
}

function findDataRow(page: Page, text: string): Locator {
  return page.locator(`[data-case-title="${text}"]`).first()
}

function findActionRow(page: Page, text: string): Locator {
  return page.locator(`.case-actions-row[data-case-title="${text}"]`).first()
}

async function openDeleteFromRow(page: Page, row: Locator) {
  await row.locator('.el-dropdown').getByRole('button').click()
  await page.getByRole('menuitem', { name: '删除' }).click()
}
