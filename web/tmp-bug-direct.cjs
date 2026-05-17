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
  await page.waitForTimeout(2000);
  console.log(JSON.stringify({ url: page.url(), text: (await page.locator('body').innerText()).slice(0, 500), messages }, null, 2));
  await context.close();
})();
