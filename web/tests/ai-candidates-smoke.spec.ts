import fs from 'node:fs'
import path from 'node:path'
import { expect, test, type Page, type Route } from '@playwright/test'

const adminUser = { username: 'superadmin', password: 'superadmin123' }
const workspaceCode = 'account-open'

const mockedGeneratorConfig = {
  id: 9001,
  workspaceCode: 'ALL',
  workspaceName: '全局',
  roleType: 'CASE_GENERATOR',
  provider: 'OpenAI',
  model: 'gpt-5.5-smoke-generator',
  baseUrl: 'https://api.openai.com/v1',
  apiKeyMasked: 'sk-***',
  apiKeyConfigured: true,
  promptTemplate: 'smoke prompt',
  reviewChecklist: null,
  temperature: 0.4,
  maxCases: 20,
  supportsImageInput: false,
  status: 1,
}

const mockedReviewerConfig = {
  ...mockedGeneratorConfig,
  id: 9002,
  roleType: 'CASE_REVIEWER',
  model: 'gpt-5.5-smoke-reviewer',
}

const mockedGeneratedCases = [
  {
    title: '成员停用后隐藏空间入口',
    caseType: 'FUNCTION',
    priority: 'P1',
    precondition: '管理员已停用目标成员',
    steps: '1. 成员重新登录平台 2. 查看顶部空间列表',
    expectedResult: '已停用成员看不到原所属空间入口',
    riskNotes: '需要确认前端缓存是否及时刷新',
    warnings: [],
  },
  {
    title: '停用成员后历史数据保持可追溯',
    caseType: 'REGRESSION',
    priority: 'P2',
    precondition: '成员在被停用前已创建用例',
    steps: '1. 管理员停用成员 2. 打开历史用例列表',
    expectedResult: '历史创建记录仍保留原创建人信息',
    riskNotes: '需要关注关联数据展示',
    warnings: ['建议补充跨空间检查'],
  },
]

const mockedReviewResult = {
  result: 'SUGGEST',
  summary: '当前候选已经覆盖主流程，但还缺停用后跨空间切换和权限刷新两类校验。',
  issues: ['缺少停用后不刷新页面直接切换空间的即时反馈验证。'],
  suggestions: ['补一条停用后跨空间切换即时刷新的边界用例', '补一条停用后接口缓存失效校验用例'],
  rawContent: JSON.stringify({
    review: 'ok',
    suggestionCount: 2,
  }),
  structured: true,
}

test.describe.serial('AI candidate edit smoke', () => {
  test('auto review summary and candidate editing flow work together', async ({ page }) => {
    test.setTimeout(90_000)
    const reviewOutputDir = path.join(process.cwd(), 'output', 'ai-review')
    fs.mkdirSync(reviewOutputDir, { recursive: true })

    await mockAiRoutes(page)
    await login(page, adminUser.username, adminUser.password)
    await page.goto(`/cases/ai-generate?workspace=${workspaceCode}`)

    const titleInput = page.locator('input[placeholder*="成员停用后按空间视角"]').first()
    await titleInput.fill('成员停用需求')
    const requirementTextarea = page.locator('textarea[placeholder*="这里填写最终要交给 AI"]').first()
    await requirementTextarea.fill('验证成员停用后空间入口、历史数据与权限状态的展示行为。')
    await page.screenshot({ path: path.join(reviewOutputDir, 'ai-generate-requirement-current.png'), fullPage: true })

    await page.getByRole('button', { name: '生成用例' }).click()
    await expect(page.getByRole('tab', { name: '用例生成', exact: true })).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText('候选用例编辑区', { exact: true })).toBeVisible()
    await expect(page.locator('.generated-case-card')).toHaveCount(2)
    const reviewBanner = page.locator('.ai-review-summary-banner')
    await expect(reviewBanner).toBeVisible()
    await expect(reviewBanner.locator('.review-result-chip')).toContainText('建议补充')
    await page.screenshot({ path: path.join(reviewOutputDir, 'ai-generate-candidates-current.png'), fullPage: true })

    await page.getByRole('button', { name: '查看评审详情' }).click()
    const reviewDrawer = page.getByRole('dialog', { name: '评审详情' })
    await expect(reviewDrawer).toBeVisible()
    await expect(reviewDrawer).toContainText('缺少停用后不刷新页面直接切换空间')
    await page.keyboard.press('Escape')
    await expect(reviewDrawer).toBeHidden()

    await page.locator('.generated-case-card').first().getByRole('button', { name: '编辑' }).click()
    await expect(page.getByText('编辑候选用例', { exact: true })).toBeVisible()
    const drawer = page.locator('.candidate-edit-drawer')
    await drawer.getByRole('textbox').first().fill('AI 候选用例已修改')
    await page.screenshot({ path: path.join(reviewOutputDir, 'ai-generate-editor-current.png'), fullPage: true })
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await expect(page.locator('.generated-case-title').first()).toContainText('成员停用后隐藏空间入口')

    await page.locator('.generated-case-card').first().getByRole('button', { name: '编辑' }).click()
    const reopenedDrawer = page.locator('.candidate-edit-drawer')
    await reopenedDrawer.getByRole('textbox').first().fill('AI 候选用例已修改')
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.el-message').last()).toContainText('候选用例已保存')
    await expect(page.locator('.generated-case-title').first()).toContainText('AI 候选用例已修改')

    await page.locator('.generated-case-card').first().getByRole('button', { name: '编辑' }).click()
    await page.getByRole('button', { name: '下一条' }).click()
    await expect(page.locator('.candidate-edit-index')).toContainText('第 2 / 2 条')
    await expect(page.locator('.candidate-edit-drawer').getByRole('textbox').first()).toHaveValue('停用成员后历史数据保持可追溯')
    await page.getByRole('button', { name: '取消', exact: true }).click()

    await page.locator('.generated-case-card').nth(1).getByRole('button', { name: '删除' }).click()
    await expect(page.locator('.generated-case-card')).toHaveCount(1)
  })

  test('manual review path works when auto review is disabled', async ({ page }) => {
    test.setTimeout(90_000)

    await mockAiRoutes(page)
    await login(page, adminUser.username, adminUser.password)
    await page.goto(`/cases/ai-generate?workspace=${workspaceCode}`)

    const titleInput = page.locator('input[placeholder*="成员停用后按空间视角"]').first()
    await titleInput.fill('成员停用需求-手动评审')
    const requirementTextarea = page.locator('textarea[placeholder*="这里填写最终要交给 AI"]').first()
    await requirementTextarea.fill('验证关闭自动评审后的手动评审链路。')

    await page.getByRole('tab', { name: '用例生成', exact: true }).click()
    await page.locator('.generate-review-toggle-row .el-switch').click()
    await expect(page.getByText('已关闭', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: '生成候选用例' }).click()
    await expect(page.locator('.generated-case-card')).toHaveCount(2)
    await expect(page.locator('.ai-review-summary-banner')).toHaveCount(0)
    await expect(page.getByText('当前还没做评审', { exact: true })).toBeVisible()
    await expect(page.getByText('手动评审。', { exact: false })).toBeVisible()

    await page.getByRole('button', { name: '评审本轮候选' }).click()
    const reviewBanner = page.locator('.ai-review-summary-banner')
    await expect(reviewBanner).toBeVisible()
    await expect(reviewBanner).toContainText('手动评审')
    await expect(reviewBanner.locator('.review-result-chip')).toContainText('建议补充')
  })
})

async function mockAiRoutes(page: Page) {
  await page.route('**/api/cases/ai/config**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        message: 'ok',
        data: {
          generatorConfig: mockedGeneratorConfig,
          reviewerConfig: mockedReviewerConfig,
        },
      }),
    })
  })

  await page.route('**/api/cases/ai/generate', async (route: Route) => {
    const request = route.request()
    const payload = JSON.parse(request.postData() ?? '{}')
    const isAppend = Array.isArray(payload.existingCases) && payload.existingCases.length > 0
    const responseCases = isAppend
      ? [
        {
          title: '补充生成-空间切换后权限即时刷新',
          caseType: 'BOUNDARY',
          priority: 'P1',
          precondition: '成员状态刚被修改',
          steps: '1. 停用成员 2. 不刷新页面直接切换空间',
          expectedResult: '空间入口与可见数据即时刷新',
          riskNotes: '需要关注前端缓存',
          warnings: [],
        },
      ]
      : mockedGeneratedCases

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        message: 'ok',
        data: {
          workspaceCode,
          workspaceName: '开户工作空间',
          provider: mockedGeneratorConfig.provider,
          model: mockedGeneratorConfig.model,
          systemMaxCases: mockedGeneratorConfig.maxCases,
          requestedMaxCases: payload.maxCases ?? 12,
          effectiveMaxCases: payload.maxCases ?? 12,
          actualGeneratedCount: responseCases.length,
          generatedCases: responseCases,
          warnings: [],
          invalidCases: [],
          rawContent: JSON.stringify(responseCases),
        },
      }),
    })
  })

  await page.route('**/api/cases/ai/review', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        message: 'ok',
        data: mockedReviewResult,
      }),
    })
  })
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(username)
  await page.locator('input:not([readonly])').nth(1).fill(password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/)
}
