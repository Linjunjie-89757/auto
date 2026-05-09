import { expect, test, type Locator, type Page } from '@playwright/test'

type ApiSession = {
  cookie: string
}

type ApiEnvelope<T> = {
  success: boolean
  data: T
  message: string
}

type WorkspaceItem = {
  code: string
  name: string
  description: string
  allScope: boolean
}

type WorkspaceMemberItem = {
  id: number
  userId: number
  username: string
  displayName: string
  roleCode: string
  status: number
}

type SettingsItem = {
  id: number
  workspaceCode: string
  workspaceName: string
  envName?: string
  paramName?: string
}

type PageResponse<T> = {
  items: T[]
  total: number
}

const adminUser = { username: 'zhangli', password: '123456' }
const superAdminUser = { username: 'superadmin', password: process.env.AUTO_PLATFORM_SUPER_ADMIN_PASSWORD ?? 'superadmin123' }
const memberUser = { username: 'wangxin', password: '123456' }

const smokeWorkspaceCode = `smoke-settings-ws-${Date.now()}`
const smokeWorkspaceName = 'Smoke Settings Workspace'
const smokeWorkspaceDescription = 'Smoke workspace before update'
const smokeWorkspaceDescriptionUpdated = 'Smoke workspace after update'
const smokeMemberWorkspace = 'risk-control'
const smokeMemberWorkspaceName = 'SCRM'
const smokeMemberUsername = 'zhaofeng'
const smokeBySpaceUsername = 'wangxin'
const smokeEnvName = 'smoke-risk-env'
const smokeEnvUrl = 'https://smoke-risk.example.com'
const smokeEnvJson = '{"tag":"smoke"}'
const smokeParamName = 'smoke-risk-param'
const smokeParamJson = '{"token":"risk-smoke"}'

test.describe.serial('Settings smoke', () => {
  let api: ApiSession

  test.beforeAll(async () => {
    api = await createApiContext()
    await cleanupSmokeArtifacts(api)
  })

  test.afterAll(async () => {
    await cleanupSmokeArtifacts(api)
  })

  test('admin can perform settings smoke flow', async ({ page }) => {
    await login(page, superAdminUser.username, superAdminUser.password)
    await openSettings(page, 'ALL')

    await openWorkspaceTab(page)
    await createWorkspace(page)
    await updateWorkspace(page)

    await openMemberPermissionTab(page)
    await editMember(page)
    await resetMemberPassword(page)
    await switchMemberMode(page, '按空间看')
    await selectWorkspaceInMemberTab(page, smokeWorkspaceName)
    await addWorkspaceMember(page)
    await removeWorkspaceMember(page)

    await openEnvTab(page)
    await createEnv(page)
    await toggleEnv(page)

    await openParamTab(page)
    await createParam(page)
    await toggleParam(page)

    await openWorkspaceTab(page)
    await deleteWorkspace(page)
  })

  test('super admin can access protected member management actions', async ({ page }) => {
    await login(page, superAdminUser.username, superAdminUser.password)
    await openSettings(page, 'ALL')

    await openMemberPermissionTab(page)
    await expect(findDataRow(page, superAdminUser.username)).toHaveCount(0)

    const adminRow = findDataRow(page, 'zhangli')
    await adminRow.getByRole('button', { name: '编辑' }).click()
    const dialog = page.getByRole('dialog', { name: '编辑成员' })
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(dialog).toHaveCount(0)

    await switchMemberMode(page, '按空间看')
    await selectWorkspaceInMemberTab(page, smokeMemberWorkspaceName)
    await expect(findDataRow(page, 'zhangli').getByRole('button', { name: '移除' })).toBeEnabled()
  })

  test('normal member sees read-only settings actions', async ({ page }) => {
    await login(page, memberUser.username, memberUser.password)
    await openSettings(page, 'ALL')

    await openEnvTab(page)
    await expect(page.getByRole('columnheader', { name: '所属空间' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新增环境' })).toHaveCount(0)

    await openParamTab(page)
    await expect(page.getByRole('columnheader', { name: '所属空间' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新增参数集' })).toHaveCount(0)

    await openWorkspaceTab(page)
    await expect(page.getByRole('button', { name: '新增工作空间' })).toHaveCount(0)

    await openMemberPermissionTab(page)
    await expect(page.getByRole('button', { name: '新增成员' })).toHaveCount(0)
    await switchMemberMode(page, '按空间看')
    await expect(page.getByRole('button', { name: '添加成员' })).toHaveCount(0)
  })

  test('admin can clean up settings smoke data', async ({ page }) => {
    await login(page, superAdminUser.username, superAdminUser.password)
    await openSettings(page, 'ALL')

    await openEnvTab(page)
    await deleteEnv(page)

    await openParamTab(page)
    await deleteParam(page)
  })
})

async function createApiContext() {
  const response = await fetch('http://127.0.0.1:8080/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Workspace-Code': 'ALL',
    },
    body: JSON.stringify(superAdminUser),
  })
  expect(response.ok).toBeTruthy()
  const cookie = response.headers.get('set-cookie')
  expect(cookie).toBeTruthy()
  return { cookie: cookie! }
}

async function cleanupSmokeArtifacts(api: ApiSession) {
  const smokeWorkspaceMembers = await apiGet<WorkspaceMemberItem[]>(api, `/workspaces/${smokeWorkspaceCode}/members`, false)
  const bySpaceMember = smokeWorkspaceMembers?.find(item => item.username === smokeBySpaceUsername)
  if (bySpaceMember) {
    await apiDelete(api, `/workspaces/${smokeWorkspaceCode}/members/${bySpaceMember.id}`)
  }

  const envItems = await apiGet<PageResponse<SettingsItem>>(api, '/settings/envs')
  for (const env of envItems.items.filter(item => item.envName === smokeEnvName)) {
    await apiDelete(api, `/settings/envs/${env.id}`)
  }

  const paramItems = await apiGet<PageResponse<SettingsItem>>(api, '/settings/params')
  for (const param of paramItems.items.filter(item => item.paramName === smokeParamName)) {
    await apiDelete(api, `/settings/params/${param.id}`)
  }

  const workspaces = await apiGet<WorkspaceItem[]>(api, '/workspaces')
  const smokeWorkspace = workspaces.find(item => item.code === smokeWorkspaceCode)
  if (smokeWorkspace) {
    await apiDelete(api, `/workspaces/${smokeWorkspaceCode}`)
  }
}

async function apiGet<T>(api: ApiSession, path: string, expectSuccess = true) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      Cookie: api.cookie,
      'X-Workspace-Code': 'ALL',
    },
  })
  if (!expectSuccess && !response.ok) {
    return null
  }
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
  await page.waitForURL(/\/dashboard|\/settings/)
}

async function openSettings(page: Page, targetWorkspace: string) {
  await page.goto(`/settings?workspace=${targetWorkspace}`)
  await expect(page.locator('main .page-title')).toHaveText('系统设置')
}

async function openWorkspaceTab(page: Page) {
  await page.getByRole('tab', { name: '工作空间' }).click()
  await expect(page.getByRole('columnheader', { name: '空间编码' })).toBeVisible()
}

async function openMemberPermissionTab(page: Page) {
  await page.getByRole('tab', { name: '成员权限' }).click()
  await expect(page.getByRole('radio', { name: '按成员看' })).toBeVisible()
}

async function switchMemberMode(page: Page, label: '按成员看' | '按空间看') {
  await page.locator('.mode-toolbar').getByText(label).click()
}

async function openEnvTab(page: Page) {
  await page.getByRole('tab', { name: '环境配置' }).click()
  await expect(page.getByRole('columnheader', { name: '环境名称' })).toBeVisible()
}

async function openParamTab(page: Page) {
  await page.getByRole('tab', { name: '参数配置' }).click()
  await expect(page.getByRole('columnheader', { name: '参数集名称' })).toBeVisible()
}

async function createWorkspace(page: Page) {
  await page.getByRole('button', { name: '新增工作空间' }).click()
  const dialog = page.getByRole('dialog', { name: '新增工作空间' })
  await dialog.locator('input:not([readonly])').nth(0).fill(smokeWorkspaceCode)
  await dialog.locator('input:not([readonly])').nth(1).fill(smokeWorkspaceName)
  await dialog.locator('textarea').fill(smokeWorkspaceDescription)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(findDataRow(page, smokeWorkspaceCode)).toContainText(smokeWorkspaceDescription)
}

async function updateWorkspace(page: Page) {
  const row = findDataRow(page, smokeWorkspaceCode)
  await row.getByRole('button', { name: '编辑' }).click()
  const dialog = page.getByRole('dialog', { name: '编辑工作空间' })
  await dialog.locator('input:not([readonly]):not([disabled])').first().fill(`${smokeWorkspaceName} Updated`)
  await dialog.locator('textarea').fill(smokeWorkspaceDescriptionUpdated)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(findDataRow(page, smokeWorkspaceCode)).toContainText(smokeWorkspaceDescriptionUpdated)
}

async function deleteWorkspace(page: Page) {
  const row = findDataRow(page, smokeWorkspaceCode)
  await row.getByRole('button', { name: '删除' }).click()
  await confirmDialog(page, '删除工作空间')
  await expect(findDataRow(page, smokeWorkspaceCode)).toHaveCount(0)
}

async function editMember(page: Page) {
  const row = findDataRow(page, smokeMemberUsername)
  await row.getByRole('button', { name: '编辑' }).click()
  const dialog = page.getByRole('dialog', { name: '编辑成员' })
  await expect(dialog.getByText('成员角色')).toBeVisible()
  await expect(dialog.getByText('所属空间')).toBeVisible()
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toHaveCount(0)
}

async function resetMemberPassword(page: Page) {
  const row = findDataRow(page, smokeMemberUsername)
  await row.getByRole('button', { name: '重置密码' }).click()
  await confirmDialog(page, '重置密码')
}

async function selectWorkspaceInMemberTab(page: Page, workspaceName: string) {
  await page.locator('.workspace-select .el-select__wrapper').click()
  await page.getByRole('option', { name: workspaceName }).click()
}

async function addWorkspaceMember(page: Page) {
  await page.getByRole('button', { name: '添加成员' }).click()
  const dialog = page.getByRole('dialog', { name: '批量添加空间成员' })
  await dialog.locator('.el-select').click()
  await page.getByRole('option', { name: new RegExp(smokeBySpaceUsername) }).click()
  await page.keyboard.press('Escape')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(findDataRow(page, smokeBySpaceUsername)).toBeVisible()
}

async function removeWorkspaceMember(page: Page) {
  const row = findDataRow(page, smokeBySpaceUsername)
  await row.getByRole('button', { name: '移除' }).click()
  await confirmDialog(page, '移除空间成员')
  await expect(findDataRow(page, smokeBySpaceUsername)).toHaveCount(0)
}

async function createEnv(page: Page) {
  await page.getByRole('button', { name: '新增环境' }).click()
  const dialog = page.getByRole('dialog', { name: '新增环境配置' })
  await dialog.locator('.el-select').first().click()
  await page.getByRole('option', { name: smokeMemberWorkspaceName }).click()
  await dialog.locator('input:not([readonly])').nth(0).fill(smokeEnvName)
  await dialog.locator('input:not([readonly])').nth(1).fill(smokeEnvUrl)
  await dialog.locator('textarea').fill(smokeEnvJson)
  await dialog.getByRole('button', { name: '保存' }).click()
  const row = findDataRow(page, smokeEnvName)
  await expect(row).toContainText(smokeMemberWorkspaceName)
}

async function toggleEnv(page: Page) {
  const row = findDataRow(page, smokeEnvName)
  await row.getByRole('button', { name: '停用' }).click()
  await confirmDialog(page, '停用环境')
  await expect(row).toContainText('停用')
}

async function deleteEnv(page: Page) {
  const row = findDataRow(page, smokeEnvName)
  if (await row.count() === 0) {
    return
  }
  await row.getByRole('button', { name: /删除/ }).click()
  await confirmDialog(page, '删除环境')
  await expect(findDataRow(page, smokeEnvName)).toHaveCount(0)
}

async function createParam(page: Page) {
  await page.getByRole('button', { name: '新增参数集' }).click()
  const dialog = page.getByRole('dialog', { name: '新增参数集' })
  await dialog.locator('.el-select').first().click()
  await page.getByRole('option', { name: smokeMemberWorkspaceName }).click()
  await dialog.locator('input:not([readonly])').first().fill(smokeParamName)
  await dialog.locator('textarea').fill(smokeParamJson)
  await dialog.getByRole('button', { name: '保存' }).click()
  const row = findDataRow(page, smokeParamName)
  await expect(row).toContainText(smokeMemberWorkspaceName)
}

async function toggleParam(page: Page) {
  const row = findDataRow(page, smokeParamName)
  await row.getByRole('button', { name: '停用' }).click()
  await confirmDialog(page, '停用参数集')
  await expect(row).toContainText('停用')
}

async function deleteParam(page: Page) {
  const row = findDataRow(page, smokeParamName)
  if (await row.count() === 0) {
    return
  }
  await row.getByRole('button', { name: /删除/ }).click()
  await confirmDialog(page, '删除参数集')
  await expect(findDataRow(page, smokeParamName)).toHaveCount(0)
}

async function confirmDialog(page: Page, title: string) {
  const dialog = page.getByRole('dialog', { name: title })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: /确定|确认|OK/ }).click()
  await expect(dialog).toHaveCount(0)
}

function findDataRow(page: Page, text: string): Locator {
  return page.locator('tr').filter({ hasText: text })
}
