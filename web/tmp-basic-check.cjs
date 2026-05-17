const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const page = await browser.newPage();
  await page.goto('http://127.0.0.1:4174/bugs?workspace=ALL', { waitUntil: 'networkidle' });
  await page.locator('vite-error-overlay').evaluateAll(nodes => nodes.forEach(node => node.remove())).catch(() => {});
  await page.waitForTimeout(2000);
  const actionButton = page.locator('.row-actions .el-button, .bug-actions-row .el-button').first();
  await actionButton.click({ force: true, timeout: 15000 });
  await page.waitForTimeout(800);
  await page.getByRole('button', { name: '基本信息' }).click({ force: true });
  await page.waitForTimeout(500);
  const html = await page.locator('.ms-bug-basic-editor').innerHTML();
  const values = await page.evaluate(() => {
    const rows = [...document.querySelectorAll('.ms-bug-basic-editor-row')];
    return rows.map(row => ({
      label: row.querySelector('.ms-bug-basic-editor-label')?.textContent?.trim(),
      text: row.querySelector('.ms-bug-basic-editor-field')?.textContent?.trim(),
      selected: row.querySelector('.el-select__selected-item')?.textContent?.trim(),
      inputValue: row.querySelector('input')?.value ?? null,
    }));
  });
  console.log(JSON.stringify({ values, html }, null, 2));
  await browser.close();
})();
