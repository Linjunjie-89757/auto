const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const page = await browser.newPage();
  page.on('console', msg => console.log('browser console:', msg.type(), msg.text()));
  await page.goto('http://127.0.0.1:4174/login');
  await page.locator('input:not([readonly])').nth(0).fill('zhangli');
  await page.locator('input:not([readonly])').nth(1).fill('123456');
  await page.locator('.login-submit').click();
  await page.waitForTimeout(3000);
  console.log('url after login', page.url());
  console.log('body text snippet', (await page.locator('body').innerText()).slice(0, 1000));
  await browser.close();
})();
