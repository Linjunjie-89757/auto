const { chromium } = require('playwright');
(async () => {
  const context = await chromium.launchPersistentContext('D:/CodeProject/auto/web/.pw-profile-debug', {
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    args: ['--disable-web-security']
  });
  const page = context.pages()[0] || await context.newPage();
  await page.goto('http://127.0.0.1:4174/bugs?workspace=ALL');
  await page.locator('vite-error-overlay').evaluateAll(nodes => nodes.forEach(node => node.remove())).catch(() => {});
  await page.waitForSelector('.bug-actions-row .el-button', { timeout: 15000 });
  await page.locator('.bug-actions-row .el-button').first().click({ force: true });
  await page.waitForTimeout(1000);
  const drawer = page.locator('.el-drawer:visible').last();
  await drawer.locator('.ms-bug-detail-tab').nth(2).click({ force: true });
  await page.waitForTimeout(200);
  await drawer.getByRole('button', { name: '更多' }).click({ force: true });
  await page.waitForTimeout(200);
  const menuItems = await page.locator('.ms-bug-detail-more-menu .el-dropdown-menu__item').allInnerTexts();
  await drawer.getByRole('button', { name: '分享' }).click({ force: true });
  const shareText = await page.evaluate(() => navigator.clipboard.readText());
  await drawer.getByRole('button', { name: '全屏' }).click({ force: true });
  await page.waitForLoadState('networkidle');
  console.log(JSON.stringify({ menuItems, shareText, url: page.url() }, null, 2));
  await context.close();
})();
