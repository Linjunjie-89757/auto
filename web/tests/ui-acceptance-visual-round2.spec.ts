import { test, type Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import path from 'node:path'

const superAdminUser = {
  username: process.env.AUTO_PLATFORM_SUPER_ADMIN_USERNAME ?? 'superadmin',
  password: process.env.AUTO_PLATFORM_SUPER_ADMIN_PASSWORD ?? 'superadmin123',
}

const outputDir = path.resolve(process.cwd(), 'output/playwright/ui-round2')

const pages = [
  { name: '01-settings', path: '/settings?workspace=ALL' },
  { name: '02-config-center', path: '/config-center?workspace=ALL' },
  { name: '03-api-execution', path: '/automation/api?workspace=ALL' },
  { name: '04-case-management', path: '/cases/manage?workspace=ALL' },
  { name: '05-case-ai-config', path: '/cases/ai-config?workspace=ALL' },
  { name: '06-case-ai-records', path: '/cases/ai-records?workspace=ALL' },
  { name: '07-bug-management', path: '/bugs?workspace=ALL' },
]

test('capture round 2 UI acceptance screenshots', async ({ page }) => {
  mkdirSync(outputDir, { recursive: true })
  await page.setViewportSize({ width: 1366, height: 768 })
  await login(page)

  for (const item of pages) {
    await page.goto(item.path)
    await page.waitForLoadState('domcontentloaded')
    await page.locator('.app-main').first().waitFor({ state: 'visible' })
    await page.waitForTimeout(600)
    await page.screenshot({
      path: path.join(outputDir, `${item.name}.png`),
      fullPage: true,
    })
  }
})

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(superAdminUser.username)
  await page.locator('input:not([readonly])').nth(1).fill(superAdminUser.password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard/)
}
