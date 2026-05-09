import path from 'node:path'
import { fileURLToPath } from 'node:url'
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
}

type ReportItem = {
  id: number
  reportName: string
}

type ReportDetailItem = {
  id: number
  reportName: string
  logText: string | null
  logSource: string
  attachments: Array<{
    id: number
    fileName: string
  }>
}

const adminUser = { username: 'zhangli', password: '123456' }
const __filename = fileURLToPath(import.meta.url)
const fixtureDir = path.join(path.dirname(__filename), 'fixtures')
const attachmentFixturePath = path.join(fixtureDir, 'report-attachment.txt')
const attachmentFixturePath2 = path.join(fixtureDir, 'report-attachment-2.log')
const reportPrefix = `SMOKE-REPORT-${Date.now()}`
const scopedReportName = `${reportPrefix}-API`
const scopedReportNameUpdated = `${scopedReportName}-UPDATED`
const allReportName = `${reportPrefix}-APP`

test.describe.serial('Reports CRUD smoke', () => {
  let api: ApiSession
  let tradeTaskId = 0
  let appTaskId = 0

  test.beforeAll(async () => {
    api = await createApiContext()
    tradeTaskId = await loadTaskId(api, 'api-trade-settlement')
    appTaskId = await loadTaskId(api, 'app-login-and-transfer')
    await cleanupReports(api)
  })

  test.afterAll(async () => {
    await cleanupReports(api)
  })

  test('admin can create, view, edit and delete a report in scoped workspace', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)
    await page.goto('/automation/api?workspace=trade-core')
    await expect(page.locator('main .page-title')).toBeVisible()

    await page.locator('.page-actions .el-button').nth(1).click()
    let dialog = visibleDialog(page)
    await selectDialogOption(page, dialog, 0, 0)
    await fillDialogInput(dialog, 1, scopedReportName)
    await selectDialogOption(page, dialog, 3, 1)
    await fillDialogTextarea(dialog, 4, 'Scoped report summary')
    await dialog.locator('.el-dialog__footer .el-button--primary').click()

    let row = findDataRow(page, scopedReportName)
    await expect(row).toBeVisible()

    await row.getByRole('button').nth(0).click()
    const drawer = visibleDrawer(page)
    await expect(drawer).toContainText(scopedReportName)
    await expect(drawer).toContainText('API')
    await drawer.getByLabel('失败摘要').fill('Scoped report detail summary')
    await drawer.getByLabel('执行日志').fill('Scoped report detail log')
    await drawer.getByRole('button', { name: '保存内容' }).click()
    await expect(drawer.getByLabel('执行日志')).toHaveValue('Scoped report detail log')

    await page.locator('input[type="file"]').setInputFiles([attachmentFixturePath, attachmentFixturePath2])
    await expect(drawer).toContainText('report-attachment.txt')
    await expect(drawer).toContainText('report-attachment-2.log')

    const reportId = await loadReportId(api, scopedReportName)
    await apiPut(api, `/reports/${reportId}/content`, {
      failureSummary: 'Scoped report detail summary',
      logText: 'Scoped report detail log',
      logSource: 'SYSTEM',
    })

    const reportDetail = await apiGet<ReportDetailItem>(api, `/reports/${reportId}`)
    expect(reportDetail.logText).toBe('Scoped report detail log')
    expect(reportDetail.logSource).toBe('SYSTEM')
    const attachment = reportDetail.attachments.find(item => item.fileName === 'report-attachment.txt')
    const attachment2 = reportDetail.attachments.find(item => item.fileName === 'report-attachment-2.log')
    expect(attachment).toBeTruthy()
    expect(attachment2).toBeTruthy()
    await downloadAttachment(api, reportDetail.id, attachment!.id)

    await drawer.locator('.list-row').filter({ hasText: 'report-attachment.txt' }).getByRole('button', { name: '删除' }).click()
    await expect(drawer).not.toContainText('report-attachment.txt')
    await page.keyboard.press('Escape')

    row = findDataRow(page, scopedReportName)
    await row.getByRole('button').nth(1).click()
    dialog = visibleDialog(page)
    await fillDialogInput(dialog, 1, scopedReportNameUpdated)
    await fillDialogTextarea(dialog, 4, 'Scoped report summary updated')
    await dialog.locator('.el-dialog__footer .el-button--primary').click()

    row = findDataRow(page, scopedReportNameUpdated)
    await expect(row).toContainText('Scoped report summary updated')

    await row.getByRole('button').nth(2).click()
    await confirmDangerDialog(page)
    await expect(findDataRow(page, scopedReportNameUpdated)).toHaveCount(0)
  })

  test('ALL scope requires target workspace and rejects mismatched task workspace', async ({ page }) => {
    await expect(apiPostExpectFailure(api, '/reports', {
      workspaceCode: 'risk-control',
      taskId: tradeTaskId,
      reportName: `${reportPrefix}-MISMATCH`,
      result: 'FAILED',
      logSource: 'APP',
      failureSummary: 'workspace mismatch',
    })).resolves.not.toBe('')

    await login(page, adminUser.username, adminUser.password)
    await page.goto('/automation/app?workspace=ALL')
    await expect(page.locator('main .page-title')).toBeVisible()

    await page.locator('.page-actions .el-button').nth(1).click()
    const dialog = visibleDialog(page)
    await fillDialogInput(dialog, 2, allReportName)
    await dialog.locator('.el-dialog__footer .el-button--primary').click()
    await expect(dialog).toBeVisible()
    await expect(findDataRow(page, allReportName)).toHaveCount(0)

    await apiPost(api, '/reports', {
      workspaceCode: 'risk-control',
      taskId: appTaskId,
      reportName: allReportName,
      result: 'FAILED',
      logSource: 'APP',
      failureSummary: 'created from ALL scope smoke',
    })
    await page.reload()

    const row = findDataRow(page, allReportName)
    await expect(row).toBeVisible()
    await row.getByRole('button').nth(2).click()
    await confirmDangerDialog(page)
    await expect(findDataRow(page, allReportName)).toHaveCount(0)
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

async function loadTaskId(api: ApiSession, taskName: string) {
  const page = await apiGet<PageResponse<TaskItem>>(api, '/tasks')
  const match = page.items.find(item => item.taskName === taskName)
  expect(match).toBeTruthy()
  return match!.id
}

async function loadReportId(api: ApiSession, reportName: string) {
  const page = await apiGet<PageResponse<ReportItem>>(api, '/reports')
  const match = page.items.find(item => item.reportName === reportName)
  expect(match).toBeTruthy()
  return match!.id
}

async function cleanupReports(api: ApiSession) {
  const reportPage = await apiGet<PageResponse<ReportItem>>(api, '/reports')
  for (const item of reportPage.items.filter(row => row.reportName.startsWith(reportPrefix))) {
    await apiDelete(api, `/reports/${item.id}`)
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

async function downloadAttachment(api: ApiSession, reportId: number, attachmentId: number) {
  const response = await fetch(`http://127.0.0.1:8080/api/reports/${reportId}/attachments/${attachmentId}/download`, {
    method: 'GET',
    headers: {
      Cookie: api.cookie,
      'X-Workspace-Code': 'ALL',
    },
  })
  expect(response.ok).toBeTruthy()
  const buffer = await response.arrayBuffer()
  expect(buffer.byteLength).toBeGreaterThan(0)
}

async function apiPost(api: ApiSession, path: string, body: unknown) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': 'ALL',
    },
    body: JSON.stringify(body),
  })
  expect(response.ok).toBeTruthy()
  const payload = await response.json() as ApiEnvelope<ReportItem>
  expect(payload.success).toBeTruthy()
  return payload.data
}

async function apiPostExpectFailure(api: ApiSession, path: string, body: unknown) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': 'ALL',
    },
    body: JSON.stringify(body),
  })
  expect(response.ok).toBeFalsy()
  const payload = await response.json() as ApiEnvelope<null>
  expect(payload.success).toBeFalsy()
  return payload.message
}

async function apiPut(api: ApiSession, path: string, body: unknown) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': 'ALL',
    },
    body: JSON.stringify(body),
  })
  expect(response.ok).toBeTruthy()
  const payload = await response.json() as ApiEnvelope<ReportDetailItem>
  expect(payload.success).toBeTruthy()
  return payload.data
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

function visibleOptions(page: Page): Locator {
  return page.locator('.el-select-dropdown:visible [role="option"]')
}

function dialogItem(dialog: Locator, index: number): Locator {
  return dialog.locator('.el-form-item').nth(index)
}

async function selectDialogOption(page: Page, dialog: Locator, itemIndex: number, optionIndex: number) {
  await dialogItem(dialog, itemIndex).locator('.el-select').click()
  const options = visibleOptions(page)
  await expect(options.first()).toBeVisible()
  if (optionIndex < 0) {
    await options.last().click({ force: true })
    return
  }
  await options.nth(optionIndex).click({ force: true })
}

async function fillDialogInput(dialog: Locator, itemIndex: number, value: string) {
  await dialogItem(dialog, itemIndex).locator('input:not([readonly])').fill(value)
}

async function fillDialogTextarea(dialog: Locator, itemIndex: number, value: string) {
  await dialogItem(dialog, itemIndex).locator('textarea').fill(value)
}

async function confirmDangerDialog(page: Page) {
  const dialog = page.getByRole('dialog').last()
  await dialog.getByRole('button', { name: /确定|确认|OK/ }).click()
}

function findDataRow(page: Page, text: string): Locator {
  return page.locator('tr').filter({ hasText: text })
}
