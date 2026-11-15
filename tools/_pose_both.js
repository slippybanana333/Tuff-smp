// TEMP: render OUR item + its vanilla reference side by side in third person. Usage: node tools/_pose_both.js <out.png> <itemId>
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const item = process.argv[3] || 'cloud_lance';
  const ctx = process.argv[4] || 'third';
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1400, height: 800 } });
  await page.goto('file://' + path.join(__dirname, '..', 'viewer', 'index.html'));
  await page.waitForFunction(() => window.viewer && window.viewer.ready());
  await page.evaluate(({ m, c }) => window.viewer.set({ item: m, ctx: c, spin: false, show: 'both', ref: true, compare: true, compareSet: ['ours', 'vanilla'] }), { m: item, c: ctx });
  await page.waitForFunction(() => window.viewer.ready());
  await page.waitForTimeout(300);
  const r = await page.evaluate(() => { const b = document.getElementById('c').getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height }; });
  await page.screenshot({ path: process.argv[2], clip: r });
  await browser.close();
})();
