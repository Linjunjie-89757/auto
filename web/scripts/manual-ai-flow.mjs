import fs from 'node:fs/promises'
import path from 'node:path'
import { chromium } from '@playwright/test'

const baseUrl = 'http://127.0.0.1:5173'
const outputDir = path.resolve('output/playwright')
const screenshotPath = path.join(outputDir, 'manual-ai-flow.png')

const result = {
  ok: false,
  generatedCount: 0,
  firstTitle: '',
  reviewSummary: '',
  screenshotPath,
  currentUrl: '',
  error: '',
}

await fs.mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } })

try {
  page.setDefaultTimeout(30000)

  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  const loginInputs = page.locator('input:not([readonly])')
  await loginInputs.nth(0).fill('superadmin')
  await loginInputs.nth(1).fill('superadmin123')
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/, { timeout: 30000 })

  await page.goto(`${baseUrl}/cases/ai-generate?workspace=account-open`, { waitUntil: 'networkidle' })
  await page.locator('.ai-workspace-panel').waitFor()

  const titleInput = page.locator('.requirement-stage .el-form-item .el-input__wrapper input').first()
  const textArea = page.locator('.requirement-text-area .el-textarea__inner').first()

  await titleInput.fill('Member status and workspace visibility')
  await textArea.fill([
    'Background:',
    'The platform has admins and members working across multiple workspaces.',
    '',
    'Requirement:',
    '1. When an admin disables a member, the disabled member should not appear in workspace member views.',
    '2. Disabled members cannot log in and cannot access any business module.',
    '3. Admins can still find the disabled member from the member list and re-enable the account.',
    '4. In ALL workspace view, data visibility must still follow the real workspace ownership rules.',
    '',
    'Please generate practical test cases that cover main flow, boundary conditions, abnormal branches and permission risks.',
  ].join('\n'))

  const generateButton = page.locator('.requirement-editor-actions .el-button').last()
  await generateButton.click()

  const cards = page.locator('.generated-case-card')
  await cards.first().waitFor({ timeout: 120000 })
  result.generatedCount = await cards.count()

  const firstTitleInput = cards.first().locator('.el-form-item .el-input__wrapper input').first()
  result.firstTitle = await firstTitleInput.inputValue()

  const reviewButton = page.locator('.review-panel-actions .el-button').last()
  await reviewButton.click()
  await page.locator('.ai-review-panel').waitFor({ timeout: 120000 })

  result.reviewSummary = await page.locator('.ai-review-panel .detail-meta').first().textContent().catch(() => '') || ''
  result.currentUrl = page.url()
  result.ok = true

  await page.screenshot({ path: screenshotPath, fullPage: true })
}
catch (error) {
  result.currentUrl = page.url()
  result.error = error instanceof Error ? error.message : String(error)
  await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {})
}
finally {
  await browser.close()
}

console.log(JSON.stringify(result, null, 2))
