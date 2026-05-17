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
  await page.locator('vite-error-overlay').evaluateAll(nodes => nodes.forEach(node => node.remove())).catch(() => {});
  await page.waitForSelector('.bug-actions-row .el-button', { timeout: 15000 });
  await page.locator('.bug-actions-row .el-button').first().click({ force: true });
  await page.waitForTimeout(1000);
  const drawer = page.locator('.el-drawer:visible').last();
  await drawer.locator('.ms-bug-detail-tab').nth(1).click({ force: true });
  await page.waitForTimeout(300);
  const editButton = drawer.getByRole('button', { name: 'ÄÚÈÝ±à¼­' });
  const before = await editButton.isVisible().catch(() => false);
  if (before) {
    await editButton.click({ force: true });
  }
  await page.waitForTimeout(500);
  const saveVisible = await drawer.getByRole('button', { name: '±£´æ' }).isVisible().catch(() => false);
  const editorVisible = await drawer.locator('.bug-rich-editor').isVisible().catch(() => false);
  console.log(JSON.stringify({ before, saveVisible, editorVisible, messages }, null, 2));
  await context.close();
})();
