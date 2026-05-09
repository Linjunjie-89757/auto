import path from 'node:path'
import { execFileSync } from 'node:child_process'
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

type BugSummaryItem = {
  id: number
  title: string
  workspaceCode: string
}

type CaseSummaryItem = {
  id: number
  caseNo: string
  title: string
}

type BugDetailItem = {
  id: number
  title: string
  description: string
  status: string
  assigneeId: number | null
  relatedCaseId: number | null
  relatedReportId: number | null
  relatedTaskId: number | null
  workspaceCode: string
  comments: Array<{ id: number; content: string }>
  flows: Array<{ id: number; toStatus: string }>
}

const __filename = fileURLToPath(import.meta.url)
const webRoot = path.dirname(path.dirname(__filename))
const repoRoot = path.dirname(webRoot)
const dbFilePath = path.join(repoRoot, 'server', 'data', 'auto-platform').replace(/\\/g, '/')
const h2JarPath = 'C:/Users/20245/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar'
const adminUser = { username: 'zhangli', password: '123456' }
const viewerUser = { username: 'wangxin', password: '123456' }
const smokePrefix = 'SMOKE-BIZ-'
const runToken = `${Date.now()}`
const caseBugTitle = `${smokePrefix}${runToken}-CASE`
const reportBugTitle = `${smokePrefix}${runToken}-REPORT`
const manualBugTitle = `${smokePrefix}${runToken}-MANUAL`
const manualBugTitleUpdated = `${manualBugTitle}-UPDATED`
const manualBugComment = `${smokePrefix}${runToken}-COMMENT`

test.describe.serial('Business smoke', () => {
  let api: ApiSession

  test.beforeAll(async () => {
    api = await createApiContext()
    cleanupSmokeBugs(smokePrefix)
  })

  test.afterAll(async () => {
    cleanupSmokeBugs(smokePrefix)
  })

  test('admin can run cases + automation + bugs smoke flow in ALL scope', async ({ page }) => {
    test.setTimeout(60_000)

    await login(page, adminUser.username, adminUser.password)

    await verifyCasesInAllScope(page, api)
    await createBugFromCaseViaApi(api)
    const caseBug = await requireBugByTitle(api, caseBugTitle)
    expect(caseBug.relatedCaseId).toBeTruthy()
    expect(caseBug.workspaceCode).toBe('trade-core')

    await verifyAutomationInAllScope(page)
    await createBugFromReportViaApi(api)
    const reportBug = await requireBugByTitle(api, reportBugTitle)
    expect(reportBug.relatedReportId).toBeTruthy()
    expect(reportBug.relatedTaskId).toBeTruthy()
    expect(reportBug.workspaceCode).toBe('trade-core')

    await createAndUpdateManualBugInAllScope(page, api)
    let manualBug = await requireBugByTitle(api, manualBugTitleUpdated)
    expect(manualBug.workspaceCode).toBe('risk-control')

    await openManualBugDetailAndOperate(page, manualBugTitleUpdated)
    manualBug = await requireBugByTitle(api, manualBugTitleUpdated)
    expect(manualBug.assigneeId).toBeTruthy()
    expect(manualBug.status).toBe('TODO')
    expect(manualBug.comments.some((item) => item.content === manualBugComment)).toBeTruthy()
    expect(manualBug.flows.length).toBeGreaterThanOrEqual(2)
  })

  test('viewer can read cases + bugs + automation in ALL scope', async ({ page }) => {
    await login(page, viewerUser.username, viewerUser.password)

    await page.goto('/cases/manage?workspace=ALL')
    await expect(page.locator('main .page-title')).toBeVisible()
    const caseRow = page.locator('[data-case-title]').first()
    await expect(caseRow).toBeVisible()

    await page.goto('/bugs?workspace=ALL')
    await expect(page.locator('main .page-title')).toBeVisible()
    const bugRow = findDataRow(page, manualBugTitleUpdated)
    await bugRow.scrollIntoViewIfNeeded()
    await expect(bugRow).toBeVisible()

    await page.goto('/automation/app?workspace=ALL')
    await expect(page.locator('main .page-title')).toBeVisible()
    const reportRow = findDataRow(page, 'app-login-and-transfer-report')
    await reportRow.scrollIntoViewIfNeeded()
    await expect(reportRow).toBeVisible()
  })

  test('admin can see, switch and persist unified list filters', async ({ page }) => {
    await login(page, adminUser.username, adminUser.password)

    await page.goto('/bugs?workspace=ALL')
    const bugFilters = page.locator('.list-toolbar-filters').first()
    await expect(bugFilters.locator('input[placeholder*="搜索"]').first()).toBeVisible()
    await expect(bugFilters.locator('.el-select').first()).toBeVisible()
    await bugFilters.locator('input[placeholder*="搜索"]').first().fill(manualBugTitleUpdated)
    await expect(findDataRow(page, manualBugTitleUpdated)).toBeVisible()
    await expect.poll(async () => await page.evaluate(() => localStorage.getItem('bug-list-filters-v1'))).toContain(manualBugTitleUpdated)
    await page.reload()
    await expect(bugFilters.locator('input[placeholder*="搜索"]').first()).toHaveValue(manualBugTitleUpdated)

    await page.goto('/automation/web?workspace=ALL')
    const taskToolbar = page.locator('.panel-card .list-toolbar').nth(0)
    const taskFilters = taskToolbar.locator('.list-toolbar-filters')
    await expect(taskFilters.locator('.el-select').first()).toBeVisible()
    await chooseSelectOption(page, taskFilters.locator('.el-select').first(), 'RUNNING')
    await expect.poll(async () => await page.evaluate(() => localStorage.getItem('task-list-filters-web-v1'))).toContain('"status":"RUNNING"')
    await page.reload()
    await expect(taskToolbar.locator('.list-toolbar-filters .el-select').first()).toContainText('RUNNING')

    const reportToolbar = page.locator('.panel-card .list-toolbar').nth(1)
    const reportFilters = reportToolbar.locator('.list-toolbar-filters')
    await expect(reportFilters.locator('.el-select').nth(1)).toBeVisible()
    await chooseSelectOption(page, reportFilters.locator('.el-select').nth(1), 'WEB')
    await expect.poll(async () => await page.evaluate(() => localStorage.getItem('report-list-filters-web-v1'))).toContain('"logSource":"WEB"')
    await page.reload()
    await expect(reportToolbar.locator('.list-toolbar-filters .el-select').nth(1)).toContainText('WEB')
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

function cleanupSmokeBugs(prefix: string) {
  const escapedPrefix = prefix.replace(/'/g, "''")
  runH2Sql(`DELETE FROM tb_bug_comment WHERE bug_id IN (SELECT id FROM tb_bug_info WHERE title LIKE '${escapedPrefix}%')`)
  runH2Sql(`DELETE FROM tb_bug_flow_record WHERE bug_id IN (SELECT id FROM tb_bug_info WHERE title LIKE '${escapedPrefix}%')`)
  runH2Sql(`DELETE FROM tb_bug_info WHERE title LIKE '${escapedPrefix}%'`)
}

function runH2Sql(sql: string) {
  execFileSync(resolveJavaExecutable(), [
    '-cp',
    h2JarPath,
    'org.h2.tools.Shell',
    '-url',
    `jdbc:h2:file:${dbFilePath};MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE`,
    '-user',
    'sa',
    '-password',
    '',
    '-sql',
    sql,
  ], {
    cwd: repoRoot,
    stdio: 'pipe',
  })
}

function resolveJavaExecutable() {
  if (process.env.JAVA_HOME) {
    const javaFromEnv = path.join(process.env.JAVA_HOME, 'bin', 'java.exe')
    if (path.isAbsolute(javaFromEnv)) {
      return javaFromEnv
    }
  }
  return 'java'
}

async function verifyCasesInAllScope(page: Page, api: ApiSession) {
  await page.goto('/cases/manage?workspace=ALL')
  await expect(page.locator('main .page-title')).toBeVisible()

  const casePage = await apiGet<PageResponse<CaseSummaryItem>>(api, '/cases?pageNo=1&pageSize=10')
  expect(casePage.items.length).toBeGreaterThan(0)
  const caseRow = page.locator('[data-case-title]').first()
  await expect(caseRow).toBeVisible()
  await expect(page.locator('.case-actions-row').first().getByRole('button').last()).toBeVisible()
}

async function createBugFromCaseViaApi(api: ApiSession) {
  const liveApi = await createApiContext()
  const casePage = await apiGet<PageResponse<{ id: number; caseNo: string }>>(liveApi, '/cases')
  const caseItem = casePage.items.find((item) => item.caseNo === 'CASE-00130')
  expect(caseItem).toBeTruthy()

  await apiPost(liveApi, `/cases/${caseItem!.id}/bugs`, {
    workspaceCode: 'trade-core',
    title: caseBugTitle,
    description: 'Smoke bug created from case center',
    priority: 'P1',
    severity: 'HIGH',
    assigneeId: null,
    tags: [],
  })
}

async function verifyAutomationInAllScope(page: Page) {
  await page.goto('/automation/web?workspace=ALL')
  await expect(page.locator('main .page-title')).toBeVisible()

  const reportRow = findDataRow(page, 'web-regression-payment-report')
  await reportRow.scrollIntoViewIfNeeded()
  await expect(reportRow).toBeVisible()
  await expect(reportRow.getByRole('button').first()).toBeVisible()
}

async function createBugFromReportViaApi(api: ApiSession) {
  const liveApi = await createApiContext()
  const reportPage = await apiGet<PageResponse<{ id: number; reportName: string }>>(liveApi, '/reports')
  const report = reportPage.items.find((item) => item.reportName === 'web-regression-payment-report')
  expect(report).toBeTruthy()

  await apiPost(liveApi, `/reports/${report!.id}/bugs`, {
    workspaceCode: 'trade-core',
    title: reportBugTitle,
    description: 'Smoke bug created from automation report',
    priority: 'P1',
    severity: 'HIGH',
    assigneeId: null,
    tags: [],
  })
}

async function createAndUpdateManualBugInAllScope(page: Page, api: ApiSession) {
  await page.goto('/bugs?workspace=ALL')
  await expect(page.locator('main .page-title')).toBeVisible()

  await page.locator('.page-actions .el-button--primary').click()
  let dialog = visibleDialog(page)
  await dialog.locator('.el-select').first().click()
  await page.getByRole('option').last().click()
  await dialog.locator('input:not([readonly])').first().fill(manualBugTitle)
  await dialog.locator('textarea').first().fill('Manual smoke bug created in ALL scope')
  await dialog.locator('.el-dialog__footer .el-button--primary').click()
  const createdBugId = await waitForBugId(api, manualBugTitle)
  await page.reload()
  await expect(findDataRow(page, manualBugTitle)).toBeVisible()

  const manualRow = findDataRow(page, manualBugTitle)
  await manualRow.getByRole('button').nth(1).click()
  dialog = visibleDialog(page)
  await dialog.locator('input:not([readonly])').first().fill(manualBugTitleUpdated)
  await dialog.locator('textarea').first().fill('Manual smoke bug updated in ALL scope')
  await dialog.locator('.el-dialog__footer .el-button--primary').click()
  await expect.poll(async () => await findBugIdByTitle(api, manualBugTitleUpdated)).toBe(createdBugId)
  await page.reload()
  const updatedRow = findDataRow(page, manualBugTitleUpdated)
  await expect(updatedRow).toBeVisible()
  await updatedRow.getByRole('button').first().click()
  await expect(visibleDrawer(page)).toBeVisible()
  await expect(findDataRow(page, manualBugTitleUpdated)).toBeVisible()
}

async function openManualBugDetailAndOperate(page: Page, title: string) {
  const drawer = visibleDrawer(page)
  await expect(drawer).toBeVisible()
  await expect(drawer).toContainText(title)

  const assignCard = drawer.locator('.detail-card').nth(1)
  await assignCard.locator('.el-select').click()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await assignCard.locator('.el-button--primary').click()

  const transitionCard = drawer.locator('.detail-card').nth(2)
  await transitionCard.locator('.el-select').click()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await transitionCard.locator('input:not([readonly])').last().fill('Smoke transition from detail drawer')
  await transitionCard.locator('.el-button--primary').click()

  const commentCard = drawer.locator('.detail-card').nth(3)
  await commentCard.locator('input:not([readonly])').first().fill(manualBugComment)
  await commentCard.locator('.el-button--primary').click()
  await expect(commentCard).toContainText(manualBugComment)
}

async function requireBugByTitle(api: ApiSession, title: string) {
  const liveApi = await createApiContext()
  let bugId: number | null = null

  await expect.poll(async () => {
    const bugPage = await apiGet<PageResponse<BugSummaryItem>>(liveApi, '/bugs')
    bugId = bugPage.items.find((item) => item.title === title)?.id ?? null
    return bugId
  }, {
    timeout: 10_000,
  }).not.toBeNull()

  return apiGet<BugDetailItem>(liveApi, `/bugs/${bugId!}`)
}

async function waitForBugId(api: ApiSession, title: string) {
  await expect.poll(async () => await findBugIdByTitle(api, title), {
    timeout: 10_000,
  }).not.toBeNull()
  return (await findBugIdByTitle(api, title))!
}

async function findBugIdByTitle(api: ApiSession, title: string) {
  const liveApi = await createApiContext()
  const bugPage = await apiGet<PageResponse<BugSummaryItem>>(liveApi, '/bugs')
  return bugPage.items.find((item) => item.title === title)?.id ?? null
}

async function apiGet<T>(api: ApiSession, path: string) {
  const payload = await apiRequest<T>(api, path, 'GET')
  return payload.data
}

async function apiPost<T>(api: ApiSession, path: string, body: unknown) {
  return apiRequest<T>(api, path, 'POST', body)
}

async function apiRequest<T>(api: ApiSession, path: string, method: 'GET' | 'POST', body?: unknown) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'Cookie': api.cookie,
      'X-Workspace-Code': 'ALL',
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  expect(response.ok).toBeTruthy()
  const payload = await response.json() as ApiEnvelope<T>
  expect(payload.success).toBeTruthy()
  return payload
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(username)
  await page.locator('input:not([readonly])').nth(1).fill(password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/)
}

function findDataRow(page: Page, text: string): Locator {
  return page.locator('tr').filter({ hasText: text })
}

function visibleDialog(page: Page): Locator {
  return page.locator('.el-overlay-dialog:visible').last()
}

function visibleDrawer(page: Page): Locator {
  return page.locator('.el-drawer:visible').last()
}

async function chooseSelectOption(page: Page, trigger: Locator, value: string) {
  await trigger.click()
  await page.getByRole('option', { name: value, exact: true }).click()
}
