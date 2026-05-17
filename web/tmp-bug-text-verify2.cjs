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
  await page.goto('http://127.0.0.1:4174/bugs?workspace=ALL');
  await page.locator('vite-error-overlay').evaluateAll(nodes => nodes.forEach(node => node.remove()));
  await page.waitForSelector('.bug-actions-row .el-button', { timeout: 15000 });
  await page.locator('.bug-actions-row .el-button').first().click({ force: true });
  await page.waitForTimeout(1000);
  const drawer = page.locator('.el-drawer:visible').last();
  const tabs = await drawer.locator('.ms-bug-detail-tab').allInnerTexts();
  const topActions = await drawer.locator('.ms-bug-detail-top-actions .el-button').allInnerTexts();
  console.log(JSON.stringify({ tabs, topActions, messages }, null, 2));
  await context.close();
})();
