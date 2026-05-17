const { chromium } = require('playwright');
(async () => {
  const context = await chromium.launchPersistentContext('D:/CodeProject/auto/web/.pw-profile-debug', {
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    args: ['--disable-web-security']
  });
  const page = context.pages()[0] || await context.newPage();
  const messages = [];
  page.on('console', msg => messages.push(`${msg.type()}: ${msg.text()}`));
  page.on('pageerror', err => messages.push(`pageerror: ${err.message}`));
  await page.goto('http://127.0.0.1:4174/login');
  await page.waitForSelector('input:not([readonly])', { timeout: 15000 });
  await page.locator('input:not([readonly])').nth(0).fill('zhangli');
  await page.locator('input:not([readonly])').nth(1).fill('123456');
  await page.locator('.login-submit').click();
  await page.waitForURL(/\/dashboard|\/settings|\/cases|\/bugs|\/automation/, { timeout: 15000 });
  await page.goto('http://127.0.0.1:4174/bugs?workspace=ALL');
  await page.waitForSelector('.bug-actions-row .el-button', { timeout: 15000 });
  await page.locator('.bug-actions-row .el-button').first().click();
  await page.waitForTimeout(1200);
  const drawer = page.locator('.el-drawer:visible').last();
  const tabs = await drawer.locator('.ms-bug-detail-tab').allInnerTexts();
  await drawer.locator('.ms-bug-detail-tab').nth(1).click();
  await page.waitForTimeout(300);
  const active1 = await drawer.locator('.ms-bug-detail-tab.is-active').innerText();
  await drawer.locator('.ms-bug-detail-tab').nth(2).click();
  await page.waitForTimeout(300);
  const active2 = await drawer.locator('.ms-bug-detail-tab.is-active').innerText();
  const paneCount = await drawer.locator('.ms-bug-detail-pane').count();
  console.log(JSON.stringify({ tabs, active1, active2, paneCount, messages }, null, 2));
  await context.close();
})();
