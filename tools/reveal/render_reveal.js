// node render_reveal.js <out_dir> <fps> <duration> [t1,t2,...]
// Opens reveal.html in headless Chromium (software WebGL) and writes one PNG per frame (or only the listed moments, as p_<t>.png).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const [outDir, fpsS, durS, previewS] = process.argv.slice(2);
  const fps = +fpsS, duration = +durS;
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('console:', m.text()); });
  await page.goto('file://' + path.join(__dirname, 'reveal.html'));
  await page.waitForFunction(() => (window.reveal && window.reveal.ready()) || window.revealError, null, { timeout: 120000 });
  const err = await page.evaluate(() => window.revealError);
  if (err) { console.error('boot failed:', err); process.exit(1); }
  const save = async (t, name) => {
    const url = await page.evaluate((tt) => window.reveal.frame(tt), t);
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
