import { expect, test, type Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import path from 'node:path'

const superAdminUser = {
  username: process.env.AUTO_PLATFORM_SUPER_ADMIN_USERNAME ?? 'superadmin',
  password: process.env.AUTO_PLATFORM_SUPER_ADMIN_PASSWORD ?? 'superadmin123',
}

const outputDir = path.resolve(process.cwd(), 'output/playwright/ui-round2')

test('AI config prompt editor can expand from preview', async ({ page }) => {
  mkdirSync(outputDir, { recursive: true })
  await page.setViewportSize({ width: 1366, height: 768 })
  await login(page)
  await page.goto('/cases/ai-config?workspace=ALL')
  await page.locator('.app-main').first().waitFor({ state: 'visible' })
  await page.getByRole('button', { name: /展开编辑/ }).first().click()
  await expect(page.locator('.prompt-textarea textarea').first()).toBeVisible()
  await page.screenshot({
    path: path.join(outputDir, '05-case-ai-config-expanded.png'),
    fullPage: true,
  })
})

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(superAdminUser.username)
  await page.locator('input:not([readonly])').nth(1).fill(superAdminUser.password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard/)
}
