import { expect, test } from '@playwright/test'

const adminUser = { username: 'zhangli', password: '123456' }

test.use({
  screenshot: 'off',
  trace: 'off',
  video: 'off',
  launchOptions: {
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  },
})

test('debug bug detail drawer interactions', async ({ page }) => {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(adminUser.username)
  await page.locator('input:not([readonly])').nth(1).fill(adminUser.password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/)

  await page.goto('/bugs?workspace=ALL')
  await expect(page.locator('main .page-title')).toBeVisible()

  const viewButton = page.locator('.bug-actions-row .el-button').filter({ hasText: '查看' }).first()
  await expect(viewButton).toBeVisible({ timeout: 15000 })
  await viewButton.click()

  const drawer = page.locator('.el-drawer:visible').last()
  await expect(drawer).toBeVisible({ timeout: 15000 })
  await page.screenshot({ path: 'bug-drawer-debug-open.png', fullPage: true })

  const tabs = drawer.locator('.ms-bug-detail-tab')
  await expect(tabs.first()).toBeVisible()

  const detailContent = drawer.locator('.ms-bug-detail-content')
  console.log('drawer text snippet:', await detailContent.innerText().catch(() => 'failed to read text'))
  console.log('drawer html snippet:', (await detailContent.innerHTML()).slice(0, 1500))

  await tabs.filter({ hasText: '基本信息' }).click()
  await page.waitForTimeout(500)
  await page.screenshot({ path: 'bug-drawer-debug-basic.png', fullPage: true })

  const basicPaneDisplay = await drawer.locator('.ms-bug-detail-pane').first().evaluate((el) => {
    return window.getComputedStyle(el).display
  })
  console.log('basic pane display:', basicPaneDisplay)

  await tabs.filter({ hasText: '详情' }).click()
  await page.waitForTimeout(500)
  await page.screenshot({ path: 'bug-drawer-debug-detail.png', fullPage: true })

  const activeTabText = await drawer.locator('.ms-bug-detail-tab.is-active').innerText()
  console.log('active tab:', activeTabText)
})
