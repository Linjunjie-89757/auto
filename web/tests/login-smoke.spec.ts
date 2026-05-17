import { expect, test } from '@playwright/test'

const superAdminUser = {
  username: process.env.AUTO_PLATFORM_SUPER_ADMIN_USERNAME ?? 'superadmin',
  password: process.env.AUTO_PLATFORM_SUPER_ADMIN_PASSWORD ?? 'superadmin123',
}

test('superadmin can log in and reach dashboard', async ({ page }) => {
  await page.goto('/login')

  await expect(page.locator('.login-panel')).toBeVisible()
  await page.locator('input:not([readonly])').nth(0).fill(superAdminUser.username)
  await page.locator('input:not([readonly])').nth(1).fill(superAdminUser.password)
  await page.locator('.login-submit').click()

  await page.waitForURL(/\/dashboard/)
  await expect(page.locator('main .page-title')).toBeVisible()
})
