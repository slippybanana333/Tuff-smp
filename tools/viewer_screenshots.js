// Takes screenshots of the model viewer with a headless browser (Playwright).
// Usage: node tools/viewer_screenshots.js [outDir] [filter]
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const out = process.argv[2] || path.join(__dirname, '..', 'viewer', 'screenshots');
const filter = process.argv[3] || '';
fs.mkdirSync(out, { recursive: true });
const ITEMS = ['molten_greataxe','cutlass_of_doom','sculk_mace','frost_longbow','cloud_lance','shrine_helmet','shrine_chestplate','shrine_leggings','shrine_boots','harbinger_of_death','anaklusmos','sculk_cannon','strange_cape','silvers_spawn_orb','shrine'];
const ARMOR = ['shrine_helmet','shrine_chestplate','shrine_leggings','shrine_boots'];
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--allow-file-access-from-files'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await page.goto('file://' + path.join(__dirname, '..', 'viewer', 'index.html'));
  await page.waitForFunction(() => window.viewer && window.viewer.ready());
  async function shot(name, state, clip) {
    if (filter && !name.includes(filter)) return;
    await page.evaluate((s) => window.viewer.set(s), Object.assign({ spin: false }, state));
    await page.waitForFunction(() => window.viewer.ready());
    await page.waitForTimeout(150);
    await page.evaluate((s) => window.viewer.set(s), Object.assign({ spin: false }, state));
    await page.waitForTimeout(100);
    await page.screenshot({ path: path.join(out, name + '.png'), clip: clip });
    console.log('shot', name);
  }
  const r = await page.evaluate(() => { const b = document.getElementById('c').getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height }; });
  const stage = { x: r.x, y: r.y, width: Math.min(r.width, 860), height: r.height };
  for (const it of ITEMS) {
    for (const ctx of ['model', 'gui', 'third', 'first', 'ground', 'fixed']) {
      await shot(`${it}_${ctx}`, { item: it, ctx, worn: [], compare: false }, stage);
    }
  }
  for (const a of ARMOR) await shot(`worn_${a}`, { item: a, ctx: 'third', worn: [a] }, stage);
  await shot('worn_all_four', { item: 'shrine_boots', ctx: 'third', worn: ARMOR, camera: { pos: [-40, 26, 60], target: [0, 16, 0] } }, stage);
  await shot('worn_all_four_back', { item: 'shrine_boots', ctx: 'third', worn: ARMOR, camera: { pos: [30, 28, -64], target: [0, 16, 0] } }, stage);
  await shot('worn_cape', { item: 'strange_cape', ctx: 'third', worn: ['strange_cape'], camera: { pos: [24, 26, -60], target: [0, 16, 0] } }, stage);
  await shot('compare_all', { compare: true, compareSet: ITEMS.filter(i => i !== 'shrine'), worn: [] }, { x: r.x, y: r.y, width: r.width, height: r.height });
  console.log(errors.length ? 'ERRORS:\n' + errors.join('\n') : 'no browser errors');
  await browser.close();
})();
