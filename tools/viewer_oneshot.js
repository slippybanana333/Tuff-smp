// node tools/viewer_oneshot.js <out.png> '<json state>'   - one viewer screenshot with an explicit state (used for comparisons)
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1000, height: 700 } });
  await page.goto('file://' + path.join(__dirname, '..', 'viewer', 'index.html'));
  await page.waitForFunction(() => window.viewer && window.viewer.ready());
  const st = JSON.parse(process.argv[3]);
  await page.evaluate((s) => window.viewer.set(Object.assign({ spin: false }, s)), st);
  await page.waitForFunction(() => window.viewer.ready());
  await page.waitForTimeout(200);
  await page.evaluate((s) => window.viewer.set(Object.assign({ spin: false }, s)), st);
  const r = await page.evaluate(() => { const b = document.getElementById('c').getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height }; });
  await page.screenshot({ path: process.argv[2], clip: r });
  await browser.close();
})();
