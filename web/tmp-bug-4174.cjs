const { chromium } = require('playwright');
(async () => {
  const context = await chromium.launchPersistentContext('D:/CodeProject/auto/web/.pw-profile-debug', {
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    args: ['--disable-web-security']
  });
  const page = context.pages()[0] || await context.newPage();
  page.on('console', msg => console.log('browser console:', msg.type(), msg.text()));
  await page.goto('http://127.0.0.1:4174/login');
  await page.locator('input:not([readonly])').nth(0).fill('zhangli');
  await page.locator('input:not([readonly])').nth(1).fill('123456');
  await page.locator('.login-submit').click();
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/, { timeout: 15000 });
  await page.goto('http://127.0.0.1:4174/bugs?workspace=ALL');
  await page.waitForSelector('.bug-actions-row .el-button', { timeout: 15000 });
  await page.locator('.bug-actions-row .el-button').first().click();
  await page.waitForTimeout(1000);
  const drawer = page.locator('.el-drawer:visible').last();
  console.log('visible drawers', await page.locator('.el-drawer:visible').count());
  console.log('has tabs', await drawer.locator('.ms-bug-detail-tab').count());
  console.log('tab labels', await drawer.locator('.ms-bug-detail-tab').allInnerTexts());
  await drawer.locator('.ms-bug-detail-tab').nth(1).click();
  await page.waitForTimeout(500);
  console.log('active after click', await drawer.locator('.ms-bug-detail-tab.is-active').innerText());
  console.log('detail pane visible', await drawer.locator('.ms-bug-detail-pane').nth(1).evaluate(el => getComputedStyle(el).display));
  await page.screenshot({ path: 'bug-drawer-current-4174.png', fullPage: true });
  await context.close();
})();
