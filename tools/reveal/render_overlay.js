// node render_overlay.js <page.html> <out_dir> <fps> <duration> [t1,t2,...]
// Opens an overlay page (window.overlay.frame(t) -> PNG data URL, with real transparency) in headless Chromium and writes one PNG per frame
// (or only the listed moments, as p_<t>.png).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const [page_, outDir, fpsS, durS, previewS] = process.argv.slice(2);
  const fps = +fpsS, duration = +durS;
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('console:', m.text()); });
  await page.goto('file://' + path.join(__dirname, page_));
  await page.waitForFunction(() => (window.overlay && window.overlay.ready()) || window.overlayError, null, { timeout: 120000 });
  const err = await page.evaluate(() => window.overlayError);
  if (err) { console.error('boot failed:', err); process.exit(1); }
  const save = async (t, name) => {
    const url = await page.evaluate((tt) => window.overlay.frame(tt), t);
    fs.writeFileSync(path.join(outDir, name), Buffer.from(url.split(',')[1], 'base64'));
  };
  if (previewS) {
    for (const s of previewS.split(',')) await save(+s, 'p_' + (+s).toFixed(2) + '.png');
  } else {
    const n = Math.round(duration * fps), t0 = Date.now();
    for (let i = 0; i < n; i++) {
      await save(i / fps, 'f_' + String(i + 1).padStart(5, '0') + '.png');
      if (i % 30 === 0) console.log(`frame ${i + 1}/${n}  (${((Date.now() - t0) / 1000).toFixed(0)}s)`);
    }
  }
  await browser.close();
})();
