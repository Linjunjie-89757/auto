import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'

const adminUser = { username: 'superadmin', password: 'superadmin123' }
const workspaceCode = 'account-open'
const apiBase = 'http://127.0.0.1:8080/api'

type RoleType = 'CASE_GENERATOR' | 'CASE_REVIEWER'

type AiConfigBackup = {
  id: number
  roleType: RoleType
  provider: string
  model: string
  baseUrl: string
  promptTemplate: string
  reviewChecklist: string | null
  temperature: number
  maxCases: number
  status: number
  apiKey: string | null
}

test.describe.serial('AI module smoke', () => {
  test.setTimeout(90_000)

  test('admin can maintain global AI configs and see V3 generate workbench', async ({ page, request }) => {
    await loginApi(request)
    const backup = await backupAiConfigs(request)

    try {
      await login(page, adminUser.username, adminUser.password)

      await page.goto('/cases/ai-config?workspace=ALL')
      await expect(page.locator('main .page-title')).toContainText('AI 配置')

      const generatorCard = findConfigCard(page, '用例生成模型')
      await fillConfigCard(generatorCard, 'gpt-5.5-smoke-generator', 'https://api.openai.com/v1', 'smoke-demo-key-generator')
      await generatorCard.getByRole('button', { name: '保存配置' }).click()
      await expectLatestMessage(page, '用例生成模型已保存')

      const reviewerCard = findConfigCard(page, '用例评审模型')
      await fillConfigCard(reviewerCard, 'gpt-5.5-smoke-reviewer', 'https://api.openai.com/v1', 'smoke-demo-key-reviewer')
      await reviewerCard.getByRole('button', { name: '保存配置' }).click()
      await expectLatestMessage(page, '用例评审模型已保存')

      await expect(generatorCard.locator('.ai-config-summary')).toContainText('全局已保存该模型配置')
      await expect(generatorCard.locator('.ai-config-summary')).toContainText('gpt-5.5-smoke-generator')
      await expect(reviewerCard.locator('.ai-config-summary')).toContainText('全局已保存该模型配置')
      await expect(reviewerCard.locator('.ai-config-summary')).toContainText('gpt-5.5-smoke-reviewer')

      await page.goto(`/cases/ai-generate?workspace=${workspaceCode}`)
      await expect(page.locator('main .page-title')).toContainText('AI 用例生成')
      await expect(page.getByRole('button', { name: '上传文档' })).toBeVisible()
      await expect(page.getByRole('button', { name: '上传图片' })).toBeVisible()
      await expect(page.getByRole('button', { name: '生成用例' })).toBeVisible()

      const titleInput = page.locator('input[placeholder*="成员停用后按空间视角"]').first()
      await titleInput.fill('AI Smoke Requirement')
      const requirementTextarea = page.locator('textarea[placeholder*="这里填写最终要交给 AI"]').first()
      await requirementTextarea.fill('验证 AI 用例生成页的需求录入、全局配置显示和 V3 生成工作台结构。')

      await page.getByRole('tab', { name: '用例生成', exact: true }).click()
      await expect(page.getByText('生成操作', { exact: true })).toBeVisible()
      await expect(page.getByText('评审控制', { exact: true })).toBeVisible()
      await expect(page.getByText('自动评审', { exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: '生成候选用例' })).toBeEnabled()
      await expect(page.getByRole('button', { name: '评审本轮候选' })).toBeDisabled()
      await expect(page.getByText('还没有评审结果', { exact: true })).toBeVisible()
    } finally {
      await restoreAiConfigs(request, backup)
    }
  })
})

function findConfigCard(page: Page, title: string): Locator {
  return page.locator('.ai-config-card').filter({ has: page.getByText(title, { exact: true }) }).first()
}

async function fillConfigCard(card: Locator, model: string, baseUrl: string, apiKey: string) {
  const textInputs = card.locator('input:not([readonly])')
  await textInputs.nth(0).fill(model)
  await textInputs.nth(1).fill(baseUrl)
  await textInputs.nth(2).fill(apiKey)

  const textareas = card.locator('textarea')
  await textareas.nth(0).fill(`你是 ${model}，请输出清晰、可执行、可验证的测试用例内容。`)
  await textareas.nth(1).fill(`请使用 ${model} 输出结构化、可阅读、可落地的补充建议。`)
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(username)
  await page.locator('input:not([readonly])').nth(1).fill(password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/)
}

async function expectLatestMessage(page: Page, text: string) {
  await expect(page.locator('.el-message').last()).toContainText(text)
}

async function loginApi(request: APIRequestContext) {
  const response = await request.post(`${apiBase}/auth/login`, {
    headers: {
      'X-Workspace-Code': 'ALL',
      'Content-Type': 'application/json',
    },
    data: adminUser,
  })
  expect(response.ok()).toBeTruthy()
}

async function backupAiConfigs(request: APIRequestContext): Promise<AiConfigBackup[]> {
  const configResponse = await request.get(`${apiBase}/cases/ai/config`, {
    headers: {
      'X-Workspace-Code': 'ALL',
    },
  })
  expect(configResponse.ok()).toBeTruthy()
  const payload = await configResponse.json()
  const items = [payload.data.generatorConfig, payload.data.reviewerConfig].filter(Boolean)
  const backups: AiConfigBackup[] = []
  for (const item of items) {
    const secretResponse = await request.get(`${apiBase}/cases/ai/config/${item.id}/secret`, {
      headers: {
        'X-Workspace-Code': 'ALL',
      },
    })
    let secretValue: string | null = null
    if (secretResponse.ok()) {
      const secretPayload = await secretResponse.json()
      secretValue = secretPayload.data.apiKey ?? null
    }
    backups.push({
      id: item.id,
      roleType: item.roleType,
      provider: item.provider,
      model: item.model,
      baseUrl: item.baseUrl,
      promptTemplate: item.promptTemplate,
      reviewChecklist: item.reviewChecklist,
      temperature: item.temperature,
      maxCases: item.maxCases,
      status: item.status,
      apiKey: secretValue,
    })
  }
  return backups
}

async function restoreAiConfigs(request: APIRequestContext, backups: AiConfigBackup[]) {
  for (const item of backups) {
    const response = await request.put(`${apiBase}/cases/ai/config/${item.id}`, {
      headers: {
        'X-Workspace-Code': 'ALL',
        'Content-Type': 'application/json',
      },
      data: {
        roleType: item.roleType,
        provider: item.provider,
        model: item.model,
        baseUrl: item.baseUrl,
        apiKey: item.apiKey ?? '',
        promptTemplate: item.promptTemplate,
        reviewChecklist: item.reviewChecklist ?? '',
        temperature: item.temperature,
        maxCases: item.maxCases,
        status: item.status,
      },
    })
    expect(response.ok()).toBeTruthy()
  }
}
