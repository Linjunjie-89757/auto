const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const page = await browser.newPage();
  page.on('console', msg => console.log('console:', msg.type(), msg.text()));
  page.on('pageerror', err => console.log('pageerror:', err.message));
  await page.goto('http://127.0.0.1:4174/login', { waitUntil: 'networkidle' });
  console.log('url', page.url());
  console.log('title', await page.title());
  console.log('body', (await page.locator('body').innerText()).slice(0, 800));
  await browser.close();
})();
