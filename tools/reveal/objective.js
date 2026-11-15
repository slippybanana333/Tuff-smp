/* Tuff SMP "New Objective" overlay - a deterministic, frame-by-frame renderer with REAL transparency.
 *
 * Remake of the owner's reference clip (a glassy "New Objective" notification card in the top-left: glowing white border, big star + runes behind a
 * tilted item, a glitchy typed title, a thin underline, two lines of text, a scrambled rune row and five small stars; it wipes in from the left edge,
 * holds, and wipes back out). The item icon is the item's real 3D model rendered with three.js.
 * window.overlay.frame(t) renders ONE frame at time t (seconds) and returns a PNG data URL (transparent background unless C.checker).
 * Everything is driven by window.OBJ (objective_data.js, written by tools/make_objective.py).
 */
(function () {
  'use strict';
  // (the headless software GPU canvas clears the area round a shadowBlur stroke inside a clip; CPU canvases do not)
  const _getContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (type, opts) { if (type === '2d') opts = Object.assign({ willReadFrequently: true }, opts || {}); return _getContext.call(this, type, opts); };
  const D = window.OBJ, C = D.config, T = C.timeline, TH = C.theme;
  const W = C.width, H = C.height;
  const CARD = { x: 60, y: 56, w: 788, h: 290, r: 24 }, PAD = 44;
  const CW = CARD.w + PAD * 2, CH = CARD.h + PAD * 2;

  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const lerp = (a, b, t) => a + (b - a) * t;
  const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
  const easeOutCubic = (x) => 1 - Math.pow(1 - x, 3);
  const easeInCubic = (x) => x * x * x;
  function rng(seed) { let s = seed >>> 0; return () => { s = (s + 0x6D2B79F5) >>> 0; let t = s; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
  const canvas = (w, h) => { const c = document.createElement('canvas'); c.width = Math.max(1, Math.round(w)); c.height = Math.max(1, Math.round(h)); return c; };
  function loadImage(url) { return new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => rej(new Error('image failed')); i.src = url; }); }
  function rrPath(g, x, y, w, h, r) {
    g.beginPath(); g.moveTo(x + r, y); g.lineTo(x + w - r, y); g.quadraticCurveTo(x + w, y, x + w, y + r); g.lineTo(x + w, y + h - r);
    g.quadraticCurveTo(x + w, y + h, x + w - r, y + h); g.lineTo(x + r, y + h); g.quadraticCurveTo(x, y + h, x, y + h - r); g.lineTo(x, y + r); g.quadraticCurveTo(x, y, x + r, y); g.closePath();
  }

  const out = canvas(W, H), octx = out.getContext('2d');
  document.body.appendChild(out);
  const cardC = canvas(CW, CH), cctx = cardC.getContext('2d');
  const bgC = canvas(CW, CH);
  const maskC = canvas(CW, CH), mctx = maskC.getContext('2d');
  const imgs = {}, gw = { font: {}, sga: {} };
  let iconC = null, ready = false;

  // ---------------------------------------------------------------- Minecraft font (normal + enchanting-table runes)
  function measure(img, into) {
    const c = canvas(img.width, img.height), g = c.getContext('2d'); g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, img.width, img.height).data;
    for (let code = 32; code < 127; code++) {
      const cx = (code % 16) * 8, cy = Math.floor(code / 16) * 8; let w = 0;
      for (let x = 0; x < 8; x++) for (let y = 0; y < 8; y++) if (d[((cy + y) * img.width + cx + x) * 4 + 3] > 0) w = Math.max(w, x + 1);
      into[code] = code === 32 ? 3 : w;
    }
  }
  const textCache = {};
  /** text drawn with the pixel font at an integer scale S, filled with `fill` (colour or fn(g,w,h)->style); bold = each glyph drawn twice, one pixel apart */
  function textCanvas(face, text, S, fill, bold) {
    const key = [face, text, S, typeof fill === 'function' ? fill.name : fill, bold ? 1 : 0].join('|');
    if (textCache[key]) return textCache[key];
    const widths = gw[face], img = imgs[face];
    let total = 0; for (const ch of text) total += (widths[ch.charCodeAt(0)] || 5) + 1 + (bold ? 1 : 0);
    const c = canvas((total + 2) * S, 10 * S), g = c.getContext('2d'); g.imageSmoothingEnabled = false;
    let x = S;
    for (const ch of text) {
      const code = ch.charCodeAt(0), w = widths[code] || 5, sx = (code % 16) * 8, sy = Math.floor(code / 16) * 8;
      g.drawImage(img, sx, sy, 8, 8, x, S, 8 * S, 8 * S);
      if (bold) g.drawImage(img, sx, sy, 8, 8, x + S, S, 8 * S, 8 * S);
      x += (w + 1 + (bold ? 1 : 0)) * S;
    }
    g.globalCompositeOperation = 'source-in';
    g.fillStyle = typeof fill === 'function' ? fill(g, c.width, c.height) : fill; g.fillRect(0, 0, c.width, c.height);
    textCache[key] = c; return c;
  }
  function drawText(g, c, S, x, y, scale, alpha) {
    g.save(); g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high'; if (alpha !== undefined) g.globalAlpha *= alpha;
    g.drawImage(c, x, y, c.width * scale / S, c.height * scale / S); g.restore();
  }
  function titleFill(g, w, h) { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0.1, TH.titleTop); gr.addColorStop(0.55, TH.titleMid); gr.addColorStop(1, TH.titleBot); return gr; }

  // ---------------------------------------------------------------- static card background (dark glass, soft streaks, star, faint motifs, border)
  function star(g, cx, cy, R, r, rot) {
    g.beginPath();
    for (let i = 0; i < 10; i++) { const a = rot + i * Math.PI / 5, rr = i % 2 ? r : R; const x = cx + Math.cos(a) * rr, y = cy + Math.sin(a) * rr; i ? g.lineTo(x, y) : g.moveTo(x, y); }
    g.closePath();
  }
  function star4(g, x, y, r) {
    g.beginPath(); g.moveTo(x, y - r); g.quadraticCurveTo(x, y, x + r, y); g.quadraticCurveTo(x, y, x, y + r); g.quadraticCurveTo(x, y, x - r, y); g.quadraticCurveTo(x, y, x, y - r); g.closePath();
  }
  function leaf(g, x, y, s, rot, alpha) {
    g.save(); g.translate(x, y); g.rotate(rot); g.globalAlpha = alpha; g.fillStyle = '#ffffff';
    g.beginPath(); g.moveTo(0, -s); g.bezierCurveTo(s * 0.9, -s * 0.5, s * 0.8, s * 0.6, 0, s); g.bezierCurveTo(-s * 0.6, s * 0.5, -s * 0.5, -s * 0.5, 0, -s); g.fill(); g.restore();
  }
  const STAR = { x: 126, y: 150, R: 132 };
  function buildBg() {
    const g = bgC.getContext('2d'); g.clearRect(0, 0, CW, CH);
    g.save(); g.translate(PAD, PAD);
    rrPath(g, 0, 0, CARD.w, CARD.h, CARD.r); g.save(); g.clip();
    let gr = g.createLinearGradient(0, 0, CARD.w, CARD.h);
    gr.addColorStop(0, TH.bg0); gr.addColorStop(0.5, TH.bg1); gr.addColorStop(1, TH.bg2); g.fillStyle = gr; g.fillRect(0, 0, CARD.w, CARD.h);
    // soft diagonal streaks (the glassy bands in the reference)
    const rnd = rng(77);
    for (let k = 0; k < 5; k++) {
      g.save(); g.translate(lerp(120, CARD.w - 60, rnd()), lerp(40, CARD.h - 40, rnd())); g.rotate(-0.5 + (rnd() - 0.5) * 0.3);
      const bw = lerp(28, 70, rnd()), sg = g.createLinearGradient(0, -bw, 0, bw), a = lerp(0.18, 0.42, rnd());
      sg.addColorStop(0, 'rgba(' + TH.streak + ',0)'); sg.addColorStop(0.5, 'rgba(' + TH.streak + ',' + a + ')'); sg.addColorStop(1, 'rgba(' + TH.streak + ',0)');
      g.globalCompositeOperation = 'screen'; g.filter = 'blur(12px)'; g.fillStyle = sg; g.fillRect(-CARD.w, -bw, CARD.w * 2, bw * 2); g.restore();
    }
    // bright glow bottom-right, dark top-left
    gr = g.createRadialGradient(CARD.w * 0.86, CARD.h * 0.85, 10, CARD.w * 0.86, CARD.h * 0.85, CARD.w * 0.5);
    gr.addColorStop(0, 'rgba(' + TH.glow + ',0.75)'); gr.addColorStop(1, 'rgba(' + TH.glow + ',0)'); g.globalCompositeOperation = 'screen'; g.fillStyle = gr; g.fillRect(0, 0, CARD.w, CARD.h);
    g.globalCompositeOperation = 'source-over';
    gr = g.createRadialGradient(0, 0, 10, 0, 0, CARD.w * 0.55); gr.addColorStop(0, 'rgba(0,10,14,0.55)'); gr.addColorStop(1, 'rgba(0,10,14,0)'); g.fillStyle = gr; g.fillRect(0, 0, CARD.w, CARD.h);
    // faint motifs
    for (const [x, y, s, r] of [[150, 70, 26, 0.5], [470, 240, 24, -0.4], [700, 70, 24, 0.7], [520, 70, 18, -0.6], [740, 190, 20, 0.2]]) leaf(g, x, y, s, r, 0.08);
    // the big star (two nested outlines) behind the icon - OFF by default (owner: "remove star behind"); C.star = true brings it back
    if (C.star) {
      g.shadowColor = 'rgba(' + TH.glow + ',0.9)'; g.shadowBlur = 14; g.lineJoin = 'round';
      g.strokeStyle = 'rgba(255,255,255,0.92)'; g.lineWidth = 2.4; star(g, STAR.x, STAR.y, STAR.R, STAR.R * 0.5, -Math.PI / 2 - 0.12); g.stroke();
      g.shadowBlur = 8; g.strokeStyle = 'rgba(' + TH.glow + ',0.85)'; g.lineWidth = 2; star(g, STAR.x, STAR.y, STAR.R * 0.78, STAR.R * 0.4, -Math.PI / 2 - 0.12); g.stroke();
    }
    g.shadowBlur = 0;
    g.restore();
    // glass: bright top edge
    g.save(); rrPath(g, 0, 0, CARD.w, CARD.h, CARD.r); g.clip();
    gr = g.createLinearGradient(0, 0, 0, 22); gr.addColorStop(0, 'rgba(255,255,255,0.34)'); gr.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = gr; g.fillRect(0, 0, CARD.w, 22);
    g.restore(); g.restore();
  }

  // ---------------------------------------------------------------- the item icon: its real 3D model, rendered once with three.js
  function renderIcon() {
    const S = 640, cvs = canvas(S, S);
    const r = new THREE.WebGLRenderer({ canvas: cvs, alpha: true, antialias: true, preserveDrawingBuffer: true });
    r.setSize(S, S, false); r.outputEncoding = THREE.sRGBEncoding; r.setClearColor(0x000000, 0);
    const tex = new THREE.Texture(imgs.model); tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter; tex.encoding = THREE.sRGBEncoding; tex.flipY = false; tex.needsUpdate = true;
    const m = McModel.build(THREE, D.model.json, () => 'm', () => new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.5, emissive: 0x0a4a55, emissiveIntensity: 1 }));
    const c = new THREE.Vector3(), sz = new THREE.Vector3(); m.box.getCenter(c); m.box.getSize(sz);
    m.group.position.set(-c.x, -c.y, -c.z);
    const pivot = new THREE.Group(); pivot.add(m.group); pivot.rotation.order = 'ZYX';
    pivot.rotation.set(C.iconTilt[0] * Math.PI / 180, C.iconTilt[1] * Math.PI / 180, C.iconTilt[2] * Math.PI / 180);
    const scene = new THREE.Scene(); scene.add(pivot);
    scene.add(new THREE.AmbientLight(0xffffff, 0.95));
    const key = new THREE.DirectionalLight(0xffffff, 1.0); key.position.set(-1, 1.5, 2); scene.add(key);
    const rim = new THREE.DirectionalLight(0x40ffee, 0.7); rim.position.set(2, -1, 1); scene.add(rim);
    const half = Math.max(sz.x, sz.y, sz.z) * 0.58;
    const cam = new THREE.OrthographicCamera(-half, half, half, -half, 0.1, 500); cam.position.set(0, 0, 100); cam.lookAt(0, 0, 0);
    r.render(scene, cam);
    return cvs;
  }

  // ---------------------------------------------------------------- animation state
  const dur = C.duration;
  function cardProgress(t) {
    if (t < T.outStart) return easeOutCubic(clamp((t - T.inStart) / T.inTime));
    return 1 - easeInCubic(clamp((t - T.outStart) / T.outTime));
  }
  function glitch(t) {
    const inRange = (a, b) => t >= a && t <= b;
    let a = 0;
    if (inRange(T.inStart + 0.05, T.inStart + T.inTime + 0.25)) a = 1;
    if (inRange(T.outStart - 0.05, T.outStart + T.outTime)) a = 1;
    const k = Math.floor(t * 7), r = rng(k * 7919 + 13)();
    if (a < 1 && r < 0.14) a = 0.6;
    return a;
  }
  const typed = (t, start, time, len) => Math.floor(clamp((t - start) / time) * len + 0.0001);

  function drawContents(t) {
    const g = cctx; g.clearRect(0, 0, CW, CH);
    g.drawImage(bgC, 0, 0);
    g.save(); g.translate(PAD, PAD);
    const gl = glitch(t), rnd = rng(Math.floor(t * 30) * 101 + 5);
    // moving light sweep across the glass
    g.save(); rrPath(g, 0, 0, CARD.w, CARD.h, CARD.r); g.clip();
    const sx = ((t * 0.32) % 1.7 - 0.35) * CARD.w;
    g.translate(sx, 0); g.rotate(0.35);
    const sw = g.createLinearGradient(-60, 0, 60, 0); sw.addColorStop(0, 'rgba(255,255,255,0)'); sw.addColorStop(0.5, 'rgba(255,255,255,0.13)'); sw.addColorStop(1, 'rgba(255,255,255,0)');
    g.globalCompositeOperation = 'screen'; g.fillStyle = sw; g.fillRect(-60, -CARD.h, 120, CARD.h * 3); g.restore();
    // runes flickering round the star
    const runeRnd = rng(4242), letters = 'abcdefghijklmnopqrstuvwxyz';
    for (let i = 0; i < 16; i++) {
      const ang = runeRnd() * Math.PI * 2, rad = STAR.R * (0.55 + runeRnd() * 0.7), x = STAR.x + Math.cos(ang) * rad * 1.05, y = STAR.y + Math.sin(ang) * rad, ch = letters[Math.floor(runeRnd() * 26)];
      const ph = runeRnd() * 6, fl = 0.45 + 0.55 * Math.max(0, Math.sin(t * (1.4 + runeRnd() * 1.6) + ph)), vis = smooth(T.inStart + 0.2 + i * 0.03, T.inStart + 0.6 + i * 0.03, t);
      if (x < 8 || x > 300 || y < 8 || y > CARD.h - 12) continue;
      const c = textCanvas('sga', ch, 2, i % 3 ? TH.rune : '#ffffff', false);
      g.save(); g.translate(x, y); g.rotate((runeRnd() - 0.5) * 0.6); drawText(g, c, 2, -8, -8, 2 + (i % 3) * 0.5, fl * vis * 0.9); g.restore();
    }
    // the item icon, tilted over the star, with a white edge + teal glow
    const iconSize = 310, bob = Math.sin(t * 1.8) * 3;
    g.save(); g.filter = 'drop-shadow(0 0 2px rgba(255,255,255,0.95)) drop-shadow(0 0 14px rgba(' + TH.glow + ',0.9))';
    g.globalAlpha = smooth(T.inStart + 0.1, T.inStart + 0.5, t);
    g.drawImage(iconC, STAR.x - iconSize / 2 - 6, STAR.y - iconSize / 2 + bob + 4, iconSize, iconSize); g.restore();

    // title: typed with a glitch cursor, RGB split + sliced while glitching
    const title = C.title, nT = typed(t, T.inStart + 0.3, 0.7, title.length);
    if (nT > 0) {
      const S = 5, sc = 4.5, tx = 272, ty = 28;
      const body = textCanvas('font', title.slice(0, nT), S, titleFill, true), dark = textCanvas('font', title.slice(0, nT), S, TH.titleDark, true);
      g.save(); g.shadowColor = 'rgba(' + TH.glow + ',0.75)'; g.shadowBlur = 16;
      for (const [dx, dy] of [[-3, 0], [3, 0], [0, -3], [0, 3], [-3, 3], [3, 3]]) drawText(g, dark, S, tx + dx, ty + dy, sc);
      g.restore();
      if (gl > 0.5) {                                        // glitch: sliced + colour-split copies
        for (let s = 0; s < 4; s++) {
          g.save(); g.beginPath(); g.rect(tx - 10, ty + s * 10, 520, 10 + (s === 3 ? 20 : 0)); g.clip();
          const jx = (rnd() - 0.5) * 16 * gl; g.globalCompositeOperation = 'lighter';
          drawText(g, textCanvas('font', title.slice(0, nT), S, '#ff2d95', true), S, tx + jx - 4, ty, sc, 0.55);
          drawText(g, textCanvas('font', title.slice(0, nT), S, '#18f0ff', true), S, tx + jx + 4, ty, sc, 0.55);
          g.restore();
        }
      }
      drawText(g, body, S, tx, ty, sc);
      if (nT < title.length) { g.fillStyle = TH.titleMid; g.fillRect(tx + body.width * sc / S - 6, ty + 8, 14, 36); }
    }
    // underline grows left -> right
    const ul = smooth(T.inStart + 0.55, T.inStart + 1.05, t);
    if (ul > 0) { const lg = g.createLinearGradient(272, 0, 672, 0); lg.addColorStop(0, 'rgba(255,255,255,0.95)'); lg.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = lg; g.fillRect(272, 104, 400 * ul, 2); }
    // body text, two lines, typed
    C.lines.forEach((ln, i) => {
      const n = typed(t, T.inStart + 0.7 + i * 0.35, 0.8, ln.length);
      if (n <= 0) return;
      const S = 5, full = textCanvas('font', ln, S, '#ffffff', false), sc = Math.min(2.55, 2.55 * 486 / (full.width * 2.55 / S)), c = textCanvas('font', ln.slice(0, n), S, '#ffffff', false), cs = textCanvas('font', ln.slice(0, n), S, 'rgba(0,25,30,0.85)', false);
      const x = 272, y = 118 + i * 35;
      drawText(g, cs, S, x + 2, y + 2, sc); drawText(g, c, S, x, y, sc);
      if (gl > 0.5 && n < ln.length) drawText(g, textCanvas('font', ln.slice(0, n), S, '#18f0ff', false), S, x + 3, y, sc, 0.4);
    });
    // bottom row: scrambled runes with a scan line, then five little stars
    const rowVis = smooth(T.inStart + 1.0, T.inStart + 1.4, t);
    if (rowVis > 0) {
      const txt = C.runeText; let nR = Math.floor(txt.length * rowVis), shown = txt.slice(0, nR);
      const rc = textCanvas('sga', shown, 4, TH.rune, false);
      g.save(); g.globalAlpha = 0.95;
      const slices = gl > 0.4 ? 3 : 1;
      for (let s = 0; s < slices; s++) { g.save(); g.beginPath(); g.rect(260, 220 + s * 12, 340, 14); g.clip(); drawText(g, rc, 4, 272 + (slices > 1 ? (rnd() - 0.5) * 14 : 0), 222, 1.9); g.restore(); }
      g.fillStyle = 'rgba(255,255,255,0.7)'; g.fillRect(262, 232, 300 * rowVis, 1.5);
      g.restore();
      for (let i = 0; i < 5; i++) {
        const sx = 626 + i * 34, tw = 1 + 0.25 * Math.sin(t * 3 + i * 0.9), r = (9 + (i % 2) * 2) * tw;
        g.save(); g.globalAlpha = smooth(T.inStart + 1.0 + i * 0.06, T.inStart + 1.3 + i * 0.06, t);
        g.shadowColor = 'rgba(' + TH.glow + ',1)'; g.shadowBlur = 10; g.fillStyle = TH.star; star4(g, sx, 238, r); g.fill(); g.restore();
      }
    }
    g.restore();
    // border: white, glowing
    g.save(); g.translate(PAD, PAD); g.shadowColor = 'rgba(' + TH.glow + ',0.95)'; g.shadowBlur = 18; g.strokeStyle = 'rgba(255,255,255,0.97)'; g.lineWidth = 3;
    rrPath(g, 1.5, 1.5, CARD.w - 3, CARD.h - 3, CARD.r); g.stroke(); g.restore();
  }

  function drawChecker() {
    const s = 40; octx.fillStyle = '#1b1b1b'; octx.fillRect(0, 0, W, H); octx.fillStyle = '#0e0e0e';
    for (let y = 0; y < H; y += s) for (let x = ((y / s) % 2) * s; x < W; x += s * 2) octx.fillRect(x, y, s, s);
  }

  function frame(t) {
    octx.clearRect(0, 0, W, H);
    if (C.checker) drawChecker();
    const p = cardProgress(t);
    if (p > 0.001) {
      drawContents(t);
      const vw = p * (CW + 140), edge = 110;                         // revealed width, soft leading edge
      mctx.clearRect(0, 0, CW, CH); mctx.globalCompositeOperation = 'source-over'; mctx.drawImage(cardC, 0, 0);
      mctx.globalCompositeOperation = 'destination-in';
      let gr = mctx.createLinearGradient(vw - edge, 0, vw, 0); gr.addColorStop(0, 'rgba(0,0,0,1)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); mctx.fillStyle = gr; mctx.fillRect(0, 0, CW, CH);
      octx.drawImage(maskC, CARD.x - PAD, CARD.y - PAD);
      // motion-blurred leading edge (the reference's soft, smeared edge while it wipes)
      if (p < 0.999) {
        mctx.globalCompositeOperation = 'source-over'; mctx.clearRect(0, 0, CW, CH); mctx.filter = 'blur(9px)'; mctx.drawImage(cardC, 0, 0); mctx.filter = 'none';
        mctx.globalCompositeOperation = 'destination-in'; gr = mctx.createLinearGradient(vw - edge, 0, vw + 60, 0); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(0.62, 'rgba(0,0,0,0.7)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); mctx.fillStyle = gr; mctx.fillRect(0, 0, CW, CH);   // ONLY a fringe at the leading edge
        octx.save(); octx.globalCompositeOperation = 'lighter'; octx.drawImage(maskC, CARD.x - PAD, CARD.y - PAD); octx.restore();
      }
    }
    return out.toDataURL('image/png');
  }

  async function boot() {
    for (const k of Object.keys(D.images)) imgs[k] = await loadImage(D.images[k]);
    measure(imgs.font, gw.font); measure(imgs.sga, gw.sga);
    buildBg(); iconC = renderIcon();
    ready = true;
  }
  window.overlay = { ready: () => ready, frame, config: C, _dbg: { bgC, cardC, maskC } };
  boot().catch((e) => { window.overlayError = String(e && e.stack || e); });
})();
