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

type TaskItem = {
  id: number
  taskName: string
  status: string
}

const adminUser = { username: 'zhangli', password: '123456' }
const taskPrefix = `SMOKE-TASK-${Date.now()}`
const scopedTaskName = `${taskPrefix}-API`
const scopedTaskNameUpdated = `${scopedTaskName}-UPDATED`
const allTaskName = `${taskPrefix}-WEB`

test.describe.serial('Tasks CRUD smoke', () => {
  let api: ApiSession

  test.beforeAll(async () => {
    api = await createApiContext()
    await cleanupTasks(api)
  })

  test.afterAll(async () => {
    await cleanupTasks(api)
  })

  test('admin can create, transition, edit and delete a task in scoped workspace', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/automation/api?workspace=trade-core')
    await expect(page.locator('main .page-title')).toBeVisible()

    await page.locator('.page-actions .el-button--primary').click()
    let dialog = visibleDialog(page)
    await dialog.locator('input:not([readonly])').first().fill(scopedTaskName)
    await dialog.locator('textarea').first().fill('Scoped task summary')
    await dialog.locator('.el-dialog__footer .el-button--primary').click()
    await expect(page.locator('.el-overlay-dialog:visible')).toHaveCount(0)

    const createdTaskId = await waitForTaskId(api, scopedTaskName)
    await page.reload()

    let row = findDataRow(page, scopedTaskName)
    await expect(row).toBeVisible({ timeout: 10000 })

    await row.getByRole('button').nth(0).click()
    const drawer = visibleDrawer(page)
    await expect(drawer).toContainText(scopedTaskName)
    await drawer.getByRole('button', { name: '转为 RUNNING' }).click()
    await expect(drawer).toContainText('RUNNING')
    await expect.poll(async () => (await getTaskById(api, createdTaskId))?.status).toBe('RUNNING')

    await drawer.getByRole('button', { name: '转为 FAILED' }).click()
    await expect(drawer).toContainText('FAILED')
    await expect.poll(async () => (await getTaskById(api, createdTaskId))?.status).toBe('FAILED')
    await page.keyboard.press('Escape')

    await page.reload()
    row = findDataRow(page, scopedTaskName)
    await expect(row).toContainText('FAILED')
    await row.getByRole('button').nth(1).click()

    dialog = visibleDialog(page)
    await dialog.locator('input:not([readonly])').first().fill(scopedTaskNameUpdated)
    await dialog.locator('textarea').first().fill('Scoped task summary updated')
    await dialog.locator('.el-dialog__footer .el-button--primary').click()
    await expect(page.locator('.el-overlay-dialog:visible')).toHaveCount(0)

    await expect.poll(async () => await findTaskIdByName(api, scopedTaskNameUpdated)).toBe(createdTaskId)
    await page.reload()
    row = findDataRow(page, scopedTaskNameUpdated)
    await expect(row).toContainText('FAILED')

    await row.getByRole('button').nth(2).click()
    await confirmDangerDialog(page)
    await expect.poll(async () => await findTaskIdByName(api, scopedTaskNameUpdated)).toBeNull()
    await page.reload()
    await expect(findDataRow(page, scopedTaskNameUpdated)).toHaveCount(0)
  })

  test('ALL scope requires target workspace before creating a task', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/automation/web?workspace=ALL')
    await expect(page.locator('main .page-title')).toBeVisible()

    await page.locator('.page-actions .el-button--primary').click()
    const dialog = visibleDialog(page)
    await dialog.locator('input:not([readonly])').first().fill(allTaskName)
    await dialog.locator('.el-dialog__footer .el-button--primary').click()
    await expect(dialog).toBeVisible()
    await expect(findDataRow(page, allTaskName)).toHaveCount(0)

    await dialog.locator('.el-select').first().click()
    await page.getByRole('option').nth(1).click()
    await dialog.locator('.el-dialog__footer .el-button--primary').click()
    await expect(page.locator('.el-overlay-dialog:visible')).toHaveCount(0)

    const createdTaskId = await waitForTaskId(api, allTaskName)
    await page.reload()

    const row = findDataRow(page, allTaskName)
    await expect(row).toBeVisible({ timeout: 10000 })
    await row.getByRole('button').nth(2).click()
    await confirmDangerDialog(page)
    await expect.poll(async () => await findTaskIdByName(api, allTaskName)).toBeNull()
    await page.reload()
    await expect(findDataRow(page, allTaskName)).toHaveCount(0)
    expect(createdTaskId).toBeGreaterThan(0)
  })

  test('deleting a task with reports is blocked', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/automation/web?workspace=trade-core')
    await expect(page.locator('main .page-title')).toBeVisible()

    const row = page.getByRole('row', { name: /web-regression-payment FAILED/ })
    await expect(row).toBeVisible()
    await row.getByRole('button').nth(2).click()
    await confirmDangerDialog(page)
    await expect(page.getByRole('row', { name: /web-regression-payment FAILED/ })).toBeVisible()
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

async function cleanupTasks(api: ApiSession) {
  const taskPage = await apiGet<PageResponse<TaskItem>>(api, '/tasks')
  for (const item of taskPage.items.filter(row => row.taskName.startsWith(taskPrefix))) {
    await apiDelete(api, `/tasks/${item.id}`)
  }
}

async function waitForTaskId(api: ApiSession, taskName: string) {
  await expect.poll(async () => await findTaskIdByName(api, taskName), {
    timeout: 10000,
  }).not.toBeNull()
  return (await findTaskIdByName(api, taskName))!
}

async function findTaskIdByName(api: ApiSession, taskName: string) {
  const taskPage = await apiGet<PageResponse<TaskItem>>(api, '/tasks')
  return taskPage.items.find(item => item.taskName === taskName)?.id ?? null
}

async function getTaskById(api: ApiSession, taskId: number) {
  const taskPage = await apiGet<PageResponse<TaskItem>>(api, '/tasks')
  return taskPage.items.find(item => item.id === taskId) ?? null
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

function visibleDialog(page: Page): Locator {
  return page.locator('.el-overlay-dialog:visible').last()
}

function visibleDrawer(page: Page): Locator {
  return page.locator('.el-drawer:visible').last()
}

async function confirmDangerDialog(page: Page) {
  const dialog = page.getByRole('dialog').last()
  await dialog.getByRole('button', { name: /确定|确认|OK/ }).click()
}

function findDataRow(page: Page, text: string): Locator {
  return page.locator('tr').filter({ hasText: text })
}
