// TEMP display-iteration helper: renders an item in a context with an arbitrary display block applied.
// Usage: node tools/_pose_iter.js <out.png> <itemId> <ctx> <displayKey> <variant.json>
// variant.json = { "rotation":[x,y,z], "translation":[x,y,z], "scale":[s,s,s] } (patched in-page, files only changed on purpose later)
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const [out, item, ctx, displayKey, variantFile] = process.argv.slice(2);
  const variant = JSON.parse(fs.readFileSync(variantFile, 'utf8'));
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1000, height: 700 } });
  await page.goto('file://' + path.join(__dirname, '..', 'viewer', 'index.html'));
  await page.waitForFunction(() => window.viewer && window.viewer.ready());
  await page.evaluate(({ m, c }) => window.viewer.set({ item: m, ctx: c, spin: false }), { m: item, c: ctx });
  await page.waitForFunction(() => window.viewer.ready());
  await page.evaluate(({ key, v }) => {
    // the display is read during rebuild() - patch the model json, then force a rebuild via viewer.set()
    const d = window.VIEWER_DATA.models[window.viewer.info().model].json.display;
    d[key] = v;
  }, { key: displayKey, v: variant });
  await page.evaluate(({ m, c }) => window.viewer.set({ item: m, ctx: c, spin: false }), { m: item, c: ctx });
  await page.waitForFunction(() => window.viewer.ready());
  await page.waitForTimeout(300);
  const r = await page.evaluate(() => { const b = document.getElementById('c').getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height }; });
  await page.screenshot({ path: out, clip: r });
  await browser.close();
})();
