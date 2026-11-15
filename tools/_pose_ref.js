// TEMP: render a model's VANILLA counterpart in a context. Usage: node tools/_pose_ref.js <out.png> <itemId> <ctx>
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const item = process.argv[3] || 'cloud_lance';
  const ctx = process.argv[4] || 'third';
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1000, height: 700 } });
  await page.goto('file://' + path.join(__dirname, '..', 'viewer', 'index.html'));
  await page.waitForFunction(() => window.viewer && window.viewer.ready());
  await page.evaluate(({ m, c }) => window.viewer.set({ model: m, ctx: c, spin: false }), { m: item, c: ctx });
  await page.waitForFunction(() => window.viewer.ready());
  await page.waitForTimeout(250);
  const r = await page.evaluate(() => { const b = document.getElementById('c').getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height }; });
  await page.screenshot({ path: process.argv[2], clip: r });
  await browser.close();
})();
