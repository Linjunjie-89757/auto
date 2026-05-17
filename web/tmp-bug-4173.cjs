const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const page = await browser.newPage();
  await page.goto('http://127.0.0.1:4173/login');
  await page.locator('input:not([readonly])').nth(0).fill('zhangli');
  await page.locator('input:not([readonly])').nth(1).fill('123456');
  await page.locator('.login-submit').click();
  await page.waitForTimeout(3000);
  await page.goto('http://127.0.0.1:4173/bugs?workspace=ALL');
  await page.waitForSelector('.bug-actions-row .el-button', { timeout: 15000 });
  await page.locator('.bug-actions-row .el-button').first().click();
  await page.waitForTimeout(1000);
  const body = await page.locator('body').innerHTML();
  const idx = body.indexOf('el-drawer');
  console.log('idx', idx);
  console.log(body.slice(Math.max(0, idx - 500), idx + 5000));
  await browser.close();
})();
