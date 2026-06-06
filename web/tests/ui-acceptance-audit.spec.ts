import { expect, test, type Page } from '@playwright/test'

const superAdminUser = {
  username: process.env.AUTO_PLATFORM_SUPER_ADMIN_USERNAME ?? 'superadmin',
  password: process.env.AUTO_PLATFORM_SUPER_ADMIN_PASSWORD ?? 'superadmin123',
}

const auditPages = [
  { name: 'sample-settings', path: '/settings?workspace=ALL' },
  { name: 'sample-config-center', path: '/config-center?workspace=ALL' },
  { name: 'sample-api-execution', path: '/automation/api?workspace=ALL' },
  { name: 'case-management', path: '/cases/manage?workspace=ALL' },
  { name: 'case-ai-config', path: '/cases/ai-config?workspace=ALL' },
  { name: 'case-ai-records', path: '/cases/ai-records?workspace=ALL' },
  { name: 'bug-management', path: '/bugs?workspace=ALL' },
]

const auditViewports = [
  { name: 'desktop-1366', width: 1366, height: 768 },
  { name: 'desktop-1920', width: 1920, height: 1080 },
]

test.describe.serial('UI acceptance audit', () => {
  test('sample and complex pages keep layout stable', async ({ page }, testInfo) => {
    await login(page)

    const results: string[] = []

    for (const viewport of auditViewports) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })

      for (const auditPage of auditPages) {
        await page.goto(auditPage.path)
        await waitForPageReady(page)

        const main = page.locator('.app-main').first()
        await expect(main).toBeVisible()

        const metrics = await page.evaluate(() => {
          const documentElement = document.documentElement
          const body = document.body
          const mainElement = document.querySelector('.app-main')
          const mainRect = mainElement?.getBoundingClientRect()
          return {
            documentWidth: documentElement.scrollWidth,
            viewportWidth: documentElement.clientWidth,
            bodyWidth: body.scrollWidth,
            mainHeight: mainRect?.height ?? 0,
            mainWidth: mainRect?.width ?? 0,
            visibleTextLength: mainElement?.textContent?.trim().length ?? 0,
          }
        })

        expect(metrics.documentWidth, `${auditPage.name} should not have page-level horizontal overflow`).toBeLessThanOrEqual(metrics.viewportWidth + 4)
        expect(metrics.bodyWidth, `${auditPage.name} body should not overflow horizontally`).toBeLessThanOrEqual(metrics.viewportWidth + 4)
        expect(metrics.mainWidth, `${auditPage.name} main width should be visible`).toBeGreaterThan(320)
        expect(metrics.mainHeight, `${auditPage.name} main height should be visible`).toBeGreaterThan(320)
        expect(metrics.visibleTextLength, `${auditPage.name} should render meaningful content`).toBeGreaterThan(10)

        const screenshot = await page.screenshot({ fullPage: true })
        await testInfo.attach(`${viewport.name}-${auditPage.name}.png`, {
          body: screenshot,
          contentType: 'image/png',
        })

        results.push(`${viewport.name} ${auditPage.name}: ok`)
      }
    }

    await testInfo.attach('ui-acceptance-audit.txt', {
      body: results.join('\n'),
      contentType: 'text/plain',
    })
  })
})

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('input:not([readonly])').nth(0).fill(superAdminUser.username)
  await page.locator('input:not([readonly])').nth(1).fill(superAdminUser.password)
  await page.locator('.login-submit').click()
  await page.waitForURL(/\/dashboard/)
}

async function waitForPageReady(page: Page) {
  await page.waitForLoadState('domcontentloaded')
  await page.locator('.app-main').first().waitFor({ state: 'visible' })
  await page.waitForTimeout(350)
}
