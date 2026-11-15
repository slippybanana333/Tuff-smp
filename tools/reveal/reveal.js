/* Tuff SMP recipe reveal - a deterministic, frame-by-frame renderer.
 *
 * Remake of the owner's reference clip (crafting table + ingredients drop onto the grid, flash, the legendary appears, pixel title drops in).
 * window.reveal.frame(t) renders ONE frame at time t (seconds) and returns it as a PNG data URL; nothing depends on real time, so the Node driver
 * (render_reveal.js) can step through the whole clip and ffmpeg turns the frames into a video.
 *
 * Everything is driven by window.REVEAL (reveal_data.js, written by tools/make_reveal.py): size, title, ingredients, the item's real 3D model json,
 * and every texture as a data URL.
 */
(function () {
  'use strict';
  const R = window.REVEAL, C = R.config, T = C.timeline;
  const W = C.width, H = C.height, DEG = Math.PI / 180;

  // ---------------------------------------------------------------- small helpers
  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const lerp = (a, b, t) => a + (b - a) * t;
  const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
  const easeOutCubic = (x) => 1 - Math.pow(1 - x, 3);
  const easeInCubic = (x) => x * x * x;
  const easeOutBack = (x) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };
  function rng(seed) { let s = seed >>> 0; return () => { s = (s + 0x6D2B79F5) >>> 0; let t = s; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
  /** Smoothly interpolates through [[time, value], ...] keyframes. */
  function keys(t, k) {
    if (t <= k[0][0]) return k[0][1];
    for (let i = 1; i < k.length; i++) if (t <= k[i][0]) return lerp(k[i - 1][1], k[i][1], smooth(k[i - 1][0], k[i][0], t));
    return k[k.length - 1][1];
  }
  const toLinear = (c) => Math.pow(c, 2.2);

  function loadImage(url) { return new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => rej(new Error('image failed')); i.src = url; }); }
  function pixelTex(img, opts) {
    const t = new THREE.Texture(img);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = (opts && opts.mip) ? THREE.NearestMipmapLinearFilter : THREE.NearestFilter;
    t.encoding = THREE.sRGBEncoding;
    if (opts && opts.repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(opts.repeat, opts.repeat); }
    t.needsUpdate = true;
    return t;
  }

  // ---------------------------------------------------------------- renderer / scene
  const glCanvas = document.createElement('canvas');
  glCanvas.width = W; glCanvas.height = H;
  const renderer = new THREE.WebGLRenderer({ canvas: glCanvas, antialias: true, preserveDrawingBuffer: true, alpha: false });
  renderer.setSize(W, H, false);
  renderer.outputEncoding = THREE.sRGBEncoding;
  renderer.setClearColor(0x030101);
  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x040101, 0.075);
  const camera = new THREE.PerspectiveCamera(C.fov || 38, W / H, 0.05, 80);

  const outCanvas = document.createElement('canvas');
  outCanvas.width = W; outCanvas.height = H;
  document.body.appendChild(outCanvas);
  const ctx = outCanvas.getContext('2d');

  const imgs = {}, texs = {};
  let ready = false;

  // lights
  const ambient = new THREE.AmbientLight(0x2a140a, 1.0); scene.add(ambient);
  const keyLight = new THREE.PointLight(0xff7a2a, 1.25, 9, 1.4); keyLight.position.set(-0.7, 2.3, 1.3); scene.add(keyLight);
  const underLight = new THREE.PointLight(0xff9a30, 1.3, 4.5, 1.6); underLight.position.set(0, 0.25, 0); scene.add(underLight);
  const revealLight = new THREE.PointLight(C.accentLight || 0x5ff0ff, 0, 7, 1.4); revealLight.position.set(0, 1.9, 0); scene.add(revealLight);

  // ---------------------------------------------------------------- table (+ floor + reflection)
  const tableGroup = new THREE.Group(); scene.add(tableGroup);
  function buildTable() {
    const mk = (key, rot) => new THREE.MeshLambertMaterial({ map: texs[key], color: 0xffb98a, emissive: 0xff5a14, emissiveMap: texs[key], emissiveIntensity: 0.42 });
    const top = mk('table_top'), front = mk('table_front'), side = mk('table_side'), bottom = mk('table_side');
    // BoxGeometry face order: +x, -x, +y, -y, +z, -z
    const mats = [side, side, top, bottom, front, side];
    const box = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), mats);
    box.position.y = 0.5;
    tableGroup.add(box);
    // reflection on the glossy floor: the same box mirrored under the floor
    const mirror = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), mats);
    mirror.position.y = -0.5;
    tableGroup.add(mirror);
  }

  function glowTexture(inner, outer) {
    const c = document.createElement('canvas'); c.width = c.height = 256; const g = c.getContext('2d');
    const gr = g.createRadialGradient(128, 128, 0, 128, 128, 128);
    gr.addColorStop(0, inner); gr.addColorStop(0.35, outer); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 256, 256);
    const t = new THREE.CanvasTexture(c); t.encoding = THREE.sRGBEncoding; return t;
  }

  let floorGlow, floorGlow2;
  function buildFloor() {
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(60, 60),
      new THREE.MeshLambertMaterial({ map: texs.floor, color: 0x6e5a56, emissive: 0x1c0b05, emissiveMap: texs.floor, transparent: true, opacity: 0.78 }));
    floor.rotation.x = -Math.PI / 2;
    floor.renderOrder = 2;
    scene.add(floor);
    // the molten-orange pool the table sits in (additive, so it also lights the mirrored table)
    const gm = new THREE.MeshBasicMaterial({ map: glowTexture('rgba(255,210,90,1)', 'rgba(255,110,20,0.55)'), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
    floorGlow = new THREE.Mesh(new THREE.PlaneGeometry(5.2, 5.2), gm);
    floorGlow.rotation.x = -Math.PI / 2; floorGlow.position.y = 0.012; floorGlow.renderOrder = 3;
    scene.add(floorGlow);
    const gm2 = new THREE.MeshBasicMaterial({ map: glowTexture('rgba(255,150,40,0.9)', 'rgba(200,60,10,0.25)'), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
    floorGlow2 = new THREE.Mesh(new THREE.PlaneGeometry(14, 14), gm2);
    floorGlow2.rotation.x = -Math.PI / 2; floorGlow2.position.y = 0.014; floorGlow2.renderOrder = 3;
    scene.add(floorGlow2);
  }

  // ---------------------------------------------------------------- ingredients (extruded pixel sprites + little blocks)
  const GRID = 0.31;                        // the crafting grid on the table top: three 4-pixel cells = 0.25 block each
  const TOP_Y = 1.0;
  const items = [];

  function spriteGeometry(img, size, thick) {
    const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
    const x = c.getContext('2d'); x.drawImage(img, 0, 0);
    const w = c.width, h = c.height, d = x.getImageData(0, 0, w, h).data;
    const px = size / w, yt = thick / 2, yb = -thick / 2;
    const pos = [], col = [], idx = []; let vi = 0;
    const alpha = (i, j) => (i < 0 || j < 0 || i >= w || j >= h) ? 0 : d[(j * w + i) * 4 + 3];
    function quad(p, c3, shade) {
      for (const q of p) { pos.push(q[0], q[1], q[2]); col.push(c3[0] * shade, c3[1] * shade, c3[2] * shade); }
      idx.push(vi, vi + 1, vi + 2, vi, vi + 2, vi + 3); vi += 4;
    }
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
      if (alpha(i, j) < 20) continue;
      const o = (j * w + i) * 4, c3 = [toLinear(d[o] / 255) * 1.45, toLinear(d[o + 1] / 255) * 1.45, toLinear(d[o + 2] / 255) * 1.45];   // a little brighter than the raw texture so they pop
      const x0 = (i - w / 2) * px, x1 = x0 + px, z0 = (j - h / 2) * px, z1 = z0 + px;
      quad([[x0, yt, z0], [x0, yt, z1], [x1, yt, z1], [x1, yt, z0]], c3, 1.0);           // top (the icon)
      quad([[x0, yb, z0], [x1, yb, z0], [x1, yb, z1], [x0, yb, z1]], c3, 0.45);          // bottom
      if (alpha(i - 1, j) < 20) quad([[x0, yt, z1], [x0, yt, z0], [x0, yb, z0], [x0, yb, z1]], c3, 0.62);
      if (alpha(i + 1, j) < 20) quad([[x1, yt, z0], [x1, yt, z1], [x1, yb, z1], [x1, yb, z0]], c3, 0.62);
      if (alpha(i, j - 1) < 20) quad([[x0, yt, z0], [x1, yt, z0], [x1, yb, z0], [x0, yb, z0]], c3, 0.7);
      if (alpha(i, j + 1) < 20) quad([[x1, yt, z1], [x0, yt, z1], [x0, yb, z1], [x1, yb, z1]], c3, 0.7);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
    g.setIndex(idx); g.computeVertexNormals();
    return g;
  }

  function buildIngredients() {
    R.ingredients.forEach((ing, i) => {
      let obj, restLift, glow;
      if (ing.kind === 'sprite') {
        const geo = spriteGeometry(imgs[ing.img], 0.31, 0.034);
        obj = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ vertexColors: true, emissive: 0x120804, side: THREE.DoubleSide }));
        restLift = 0.02;
      } else {
        const s = ing.size || 0.17, hh = s * (ing.height || 1);
        const mk = (k) => new THREE.MeshLambertMaterial({ map: texs[k], color: 0xffe6d0, emissive: 0x2a1408, emissiveMap: texs[k], emissiveIntensity: 0.5, transparent: false, alphaTest: 0.4 });
        const side = mk(ing.faces.side), topM = mk(ing.faces.top), bot = mk(ing.faces.bottom);
        obj = new THREE.Mesh(new THREE.BoxGeometry(s, hh, s), [side, side, topM, bot, side, side]);
        restLift = hh / 2;
      }
      const col = ing.cell % 3, row = Math.floor(ing.cell / 3);
      const holder = new THREE.Group(); holder.add(obj); scene.add(holder);
      const rnd = rng(1234 + i * 77);
      items.push({ holder, obj, ing, i, rest: new THREE.Vector3((col - 1) * GRID, TOP_Y + restLift, (row - 1) * GRID), lift: restLift,
        spinDir: rnd() < 0.5 ? -1 : 1, startOff: new THREE.Vector3((rnd() - 0.5) * 0.5, 1.4 + rnd() * 0.9, (rnd() - 0.5) * 0.5), phase: rnd() * 6.28,
        spawn: T.itemsStart + i * T.stagger, tilt: (rnd() - 0.5) * 0.4 });
    });
  }

  // ---------------------------------------------------------------- the legendary (built from its real model json)
  const FACES = {
    down: [[0, 0, 1], [0, 0, 0], [1, 0, 0], [1, 0, 1]], up: [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]],
    north: [[1, 1, 0], [1, 0, 0], [0, 0, 0], [0, 1, 0]], south: [[0, 1, 1], [0, 0, 1], [1, 0, 1], [1, 1, 1]],
    west: [[0, 1, 0], [0, 0, 0], [0, 0, 1], [0, 1, 1]], east: [[1, 1, 1], [1, 0, 1], [1, 0, 0], [1, 1, 0]],
  };
  function elementMatrix(el) {
    const m = new THREE.Matrix4();
    const r = el.rotation; if (!r) return m;
    const o = r.origin, a = (r.angle || 0) * DEG, rm = new THREE.Matrix4();
    if (r.axis === 'x') rm.makeRotationX(a); else if (r.axis === 'y') rm.makeRotationY(a); else rm.makeRotationZ(a);
    return m.makeTranslation(o[0], o[1], o[2]).multiply(rm).multiply(new THREE.Matrix4().makeTranslation(-o[0], -o[1], -o[2]));
  }
  function modelGroup(json, tex) {
    const g = new THREE.Group(); const box = new THREE.Box3();
    for (const el of json.elements) {
      const pos = [], uvs = [], idx = [], rm = elementMatrix(el); let vi = 0;
      for (const face of Object.keys(el.faces)) {
        const fd = el.faces[face], uv = fd.uv || [0, 0, 16, 16], cs = FACES[face];
        for (let i = 0; i < 4; i++) {
          const c = cs[i];
          const v = new THREE.Vector3(c[0] ? el.to[0] : el.from[0], c[1] ? el.to[1] : el.from[1], c[2] ? el.to[2] : el.from[2]).applyMatrix4(rm);
          pos.push(v.x, v.y, v.z); box.expandByPoint(v);
          uvs.push(((i === 0 || i === 1) ? uv[0] : uv[2]) / 16, ((i === 0 || i === 3) ? uv[1] : uv[3]) / 16);
        }
        idx.push(vi, vi + 1, vi + 2, vi, vi + 2, vi + 3); vi += 4;
      }
      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
      geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
      geo.setIndex(idx); geo.computeVertexNormals();
      g.add(new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.5, color: 0xffffff, emissive: C.legendaryEmissive || 0x1f6a78, emissiveMap: tex, emissiveIntensity: 0.36 })));
    }
    return { group: g, box };
  }

  let legend, legendGlow, legendGlowMat;
  function buildLegend() {
    const m = modelGroup(R.model.json, texs.model);
    const c = new THREE.Vector3(), sz = new THREE.Vector3(); m.box.getCenter(c); m.box.getSize(sz);
    m.group.position.set(-c.x, -c.y, -c.z);                                 // centre it
    const inner = new THREE.Group(); inner.add(m.group);
    const longest = Math.max(sz.x, sz.y, sz.z);
    const s = (C.legendaryLength || 1.5) / longest;                            // model units -> blocks, longest side = legendaryLength
    inner.scale.setScalar(s);
    // lay it flat: model +Y (the shaft) along the table diagonal, model +Z (its detailed front) facing up
    const yDir = new THREE.Vector3(Math.cos((C.legendaryYaw || -28) * DEG), 0, -Math.sin((C.legendaryYaw || -28) * DEG)).normalize();
    const zUp = new THREE.Vector3(0, 1, 0);
    const xDir = new THREE.Vector3().crossVectors(yDir, zUp).normalize();
    zUp.crossVectors(xDir, yDir).normalize();
    const basis = new THREE.Matrix4().makeBasis(xDir, yDir, zUp);
    const lay = new THREE.Group(); lay.add(inner); lay.quaternion.setFromRotationMatrix(basis);
    legend = new THREE.Group(); legend.add(lay); scene.add(legend);
    legendGlowMat = new THREE.SpriteMaterial({ map: glowTexture(C.legendaryGlow[0], C.legendaryGlow[1]), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
    legendGlow = new THREE.Sprite(legendGlowMat); legendGlow.scale.set(2.0, 2.0, 1); scene.add(legendGlow);
  }

  // ---------------------------------------------------------------- particles (analytic, so any frame can be rendered on its own)
  function makeStarTexture() {
    const c = document.createElement('canvas'); c.width = c.height = 64; const g = c.getContext('2d');
    const gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.25, 'rgba(255,255,255,0.7)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
    g.strokeStyle = 'rgba(255,255,255,0.85)'; g.lineWidth = 2; g.beginPath(); g.moveTo(32, 4); g.lineTo(32, 60); g.moveTo(4, 32); g.lineTo(60, 32); g.stroke();
    const t = new THREE.CanvasTexture(c); return t;
  }
  const clouds = {};
  function cloud(name, n, size) {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(new Float32Array(n * 3), 3));
    geo.setAttribute('color', new THREE.Float32BufferAttribute(new Float32Array(n * 3), 3));
    const pts = new THREE.Points(geo, new THREE.PointsMaterial({ size, map: makeStarTexture(), vertexColors: true, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true, fog: false }));
    pts.frustumCulled = false; scene.add(pts);
    const seeds = []; const r = rng(name.length * 991 + n);
    for (let i = 0; i < n; i++) seeds.push({ a: r(), b: r(), c: r(), d: r(), e: r() });
    clouds[name] = { geo, n, seeds, pts };
  }
  function setPoint(cl, i, x, y, z, r, g, b, a) { const p = cl.geo.attributes.position.array, c = cl.geo.attributes.color.array; p[i * 3] = x; p[i * 3 + 1] = y; p[i * 3 + 2] = z; c[i * 3] = r * a; c[i * 3 + 1] = g * a; c[i * 3 + 2] = b * a; }

  function updateParticles(t) {
    const acc = C.accent || [0.4, 0.95, 1.0];
    // 1) embers rising off the hot table
    let cl = clouds.embers;
    for (let i = 0; i < cl.n; i++) {
      const s = cl.seeds[i], life = 3.4, age = ((t * 0.55 + s.a * life) % life), k = age / life;
      const x = (s.b - 0.5) * 1.3 + Math.sin(age * 1.7 + s.c * 6) * 0.08, z = (s.d - 0.5) * 1.3 + Math.cos(age * 1.3 + s.e * 6) * 0.08, y = TOP_Y + 0.05 + age * (0.25 + s.c * 0.2);
      const a = Math.sin(Math.PI * k) * (0.5 + 0.5 * Math.sin(t * 9 + s.e * 20)) * 0.9;
      setPoint(cl, i, x, y, z, 1.0, 0.55 + 0.3 * s.b, 0.18, Math.max(0, a));
    }
    cl.geo.attributes.position.needsUpdate = true; cl.geo.attributes.color.needsUpdate = true;
    // 2) puffs where each ingredient lands
    cl = clouds.landing; let n = 0;
    for (const it of items) {
      const land = it.spawn + T.fall;
      for (let j = 0; j < 8; j++, n++) {
        const age = t - land, s = cl.seeds[n], ang = (j / 8) * Math.PI * 2 + s.a;
        if (age < 0 || age > 0.7) { setPoint(cl, n, 0, -9, 0, 0, 0, 0, 0); continue; }
        const rr = 0.04 + age * (0.35 + s.b * 0.2), k = age / 0.7;
        setPoint(cl, n, it.rest.x + Math.cos(ang) * rr, TOP_Y + 0.03 + age * 0.25, it.rest.z + Math.sin(ang) * rr, 1.0, 0.8, 0.45, (1 - k) * 0.95);
      }
    }
    cl.geo.attributes.position.needsUpdate = true; cl.geo.attributes.color.needsUpdate = true;
    // 3) the burst when the legendary appears + a rising halo of spores afterwards
    cl = clouds.burst; const tb = T.reveal + T.converge;
    for (let i = 0; i < cl.n; i++) {
      const s = cl.seeds[i], age = t - tb;
      if (age < 0 || age > 2.2) { setPoint(cl, i, 0, -9, 0, 0, 0, 0, 0); continue; }
      const th = s.a * Math.PI * 2, ph = Math.acos(2 * s.b - 1), sp = 0.8 + s.c * 2.4, drag = 1 - Math.exp(-age * 2.2);
      const dx = Math.sin(ph) * Math.cos(th), dy = Math.cos(ph) * 0.8 + 0.25, dz = Math.sin(ph) * Math.sin(th);
      const k = age / 2.2;
      setPoint(cl, i, dx * sp * drag * 0.55, TOP_Y + 0.35 + dy * sp * drag * 0.55 - 0.1 * age * age, dz * sp * drag * 0.55, lerp(1, acc[0], s.d), lerp(1, acc[1], s.d), lerp(1, acc[2], s.d), Math.pow(1 - k, 1.5));
    }
    cl.geo.attributes.position.needsUpdate = true; cl.geo.attributes.color.needsUpdate = true;
    cl = clouds.spores;
    for (let i = 0; i < cl.n; i++) {
      const s = cl.seeds[i], tt = t - (T.reveal + T.converge + 0.2);
      if (tt < 0) { setPoint(cl, i, 0, -9, 0, 0, 0, 0, 0); continue; }
      const life = 3.0, age = (tt * 0.6 + s.a * life) % life, k = age / life;
      const ang = s.b * 6.28 + age * 0.4, rad = 0.3 + s.c * 0.75;
      const fadeIn = smooth(0, 0.8, tt);
      setPoint(cl, i, Math.cos(ang) * rad, TOP_Y + 0.15 + age * 0.4, Math.sin(ang) * rad, acc[0], acc[1], acc[2], Math.sin(Math.PI * k) * 0.8 * fadeIn);
    }
    cl.geo.attributes.position.needsUpdate = true; cl.geo.attributes.color.needsUpdate = true;
  }

  // ---------------------------------------------------------------- title (pixel font, drawn on a canvas, shown as a billboard behind/above the table)
  let titleCanvas, titleCtx, titleTex, titleMesh, glyphW = {};
  const FONT_S = 14;                                               // one font pixel = 14 canvas pixels
  function measureGlyphs(img) {
    const c = document.createElement('canvas'); c.width = img.width; c.height = img.height; const g = c.getContext('2d'); g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, img.width, img.height).data;
    for (let code = 32; code < 127; code++) {
      const cx = (code % 16) * 8, cy = Math.floor(code / 16) * 8; let w = 0;
      for (let x = 0; x < 8; x++) for (let y = 0; y < 8; y++) if (d[((cy + y) * img.width + cx + x) * 4 + 3] > 0) w = Math.max(w, x + 1);
      glyphW[code] = code === 32 ? 3 : w;
    }
  }
  function glyphCanvas(code, color) {
    const c = document.createElement('canvas'); c.width = 8 * FONT_S; c.height = 8 * FONT_S; const g = c.getContext('2d');
    g.imageSmoothingEnabled = false;
    g.drawImage(imgs.font, (code % 16) * 8, Math.floor(code / 16) * 8, 8, 8, 0, 0, c.width, c.height);
    g.globalCompositeOperation = 'source-in'; g.fillStyle = color; g.fillRect(0, 0, c.width, c.height);
    return c;
  }
  function buildTitle() {
    measureGlyphs(imgs.font);
    const text = C.title; let total = 0;
    for (const ch of text) total += (glyphW[ch.charCodeAt(0)] || 5) + 1;
    total -= 1;
    titleCanvas = document.createElement('canvas'); titleCanvas.width = (total + 4) * FONT_S; titleCanvas.height = 12 * FONT_S;
    titleCtx = titleCanvas.getContext('2d');
    titleTex = new THREE.CanvasTexture(titleCanvas); titleTex.encoding = THREE.sRGBEncoding; titleTex.minFilter = THREE.LinearFilter;
        titleMesh = new THREE.Mesh(new THREE.PlaneGeometry(titleCanvas.width / titleCanvas.height, 1), new THREE.MeshBasicMaterial({ map: titleTex, transparent: true, depthWrite: false, fog: false, toneMapped: false }));
    titleMesh.renderOrder = 6; titleMesh.visible = false; scene.add(titleMesh);
    titleMesh.userData.total = total;
  }
  function drawTitle(tt) {
    const g = titleCtx, text = C.title, total = titleMesh.userData.total, col = C.titleColors;
    g.clearRect(0, 0, titleCanvas.width, titleCanvas.height);
    let x = 2; const baseY = 2 * FONT_S, r = rng(9);
    for (let i = 0; i < text.length; i++) {
      const code = text.charCodeAt(i), gw = glyphW[code] || 5;
      const p = clamp((tt - i * T.letterStagger) / T.letterTime);
      if (code !== 32 && p > 0) {
        const f = (x - 2 + gw / 2) / total;                          // 0..1 across the title
        const c1 = col[0], c2 = col[1], c3 = col[2];
        const mix = (a, b, k) => a.map((v, j) => Math.round(lerp(v, b[j], k)));
        const rgb = f < 0.5 ? mix(c1, c2, f * 2) : mix(c2, c3, (f - 0.5) * 2);
        const fill = `rgb(${rgb[0]},${rgb[1]},${rgb[2]})`;
        const lift = (1 - easeOutBack(clamp(p))) * -5 * FONT_S;
        const jitter = p < 1 ? (r() - 0.5) * 0.7 * FONT_S : 0;
        const px = x * FONT_S + jitter, py = baseY + lift + (p < 1 ? (r() - 0.5) * 0.5 * FONT_S : 0);
        g.globalAlpha = clamp(p * 2.2);
        // dark outline (one font pixel all round), then a darker drop shadow, then the colour
        const dark = glyphCanvas(code, '#0b0b24'), mid = glyphCanvas(code, `rgb(${Math.round(rgb[0] * 0.35)},${Math.round(rgb[1] * 0.35)},${Math.round(rgb[2] * 0.5)})`), body = glyphCanvas(code, fill);
        const o = FONT_S * 0.9;
        for (const [dx, dy] of [[-o, 0], [o, 0], [0, -o], [0, o], [-o, -o], [o, -o], [-o, o], [o, o]]) g.drawImage(dark, px + dx, py + dy);
        g.drawImage(mid, px + FONT_S, py + FONT_S);
        g.drawImage(body, px, py);
        // light top edge: draw the lighter tint clipped to the upper half of the letter
        g.save(); g.beginPath(); g.rect(px, py, 8 * FONT_S, 3.2 * FONT_S); g.clip(); g.globalAlpha *= 0.55; g.drawImage(glyphCanvas(code, '#ffffff'), px, py - FONT_S * 0.4); g.restore();
        if (p < 1) { g.globalAlpha = (1 - p) * 0.9; g.drawImage(glyphCanvas(code, '#ffffff'), px, py); }   // white flash while the letter lands
      }
      x += gw + 1;
    }
    g.globalAlpha = 1;
    titleTex.needsUpdate = true;
  }

  // ---------------------------------------------------------------- camera
  function placeCamera(t) {
    const dbg = C.debugCam;
    const d = dbg ? dbg[0] : keys(t, T.camDist), pitch = (dbg ? dbg[1] : keys(t, T.camPitch)) * DEG, yaw = (dbg ? dbg[2] : keys(t, T.camYaw)) * DEG;
    const ty = keys(t, T.camTargetY), tx = keys(t, T.camTargetX || [[0, 0]]), tz = keys(t, T.camTargetZ || [[0, 0]]);
    const shake = 0.0025 * Math.sin(t * 37) + 0.0015 * Math.sin(t * 53 + 1);
    camera.position.set(tx + d * Math.cos(pitch) * Math.sin(yaw) + shake, ty + d * Math.sin(pitch) + shake, tz + d * Math.cos(pitch) * Math.cos(yaw));
    camera.lookAt(tx, ty, tz);
    camera.updateMatrixWorld();
    return { d, pitch, yaw, ty };
  }

  // ---------------------------------------------------------------- one frame
  const tReveal = () => T.reveal + T.converge;
  function frame(t) {
    const cam = placeCamera(t);
    // table glow breathes a little
    const pulse = 0.5 + 0.5 * Math.sin(t * 2.4);
    keyLight.intensity = 1.25 + 0.12 * pulse;
    floorGlow.material.opacity = 0.85 + 0.15 * pulse;
    floorGlow.scale.setScalar(1 + 0.03 * pulse);
    // ingredients
    for (const it of items) {
      const age = t - it.spawn, f = clamp(age / T.fall), conv = clamp((t - T.reveal) / T.converge);
      const visible = age > 0 && t < T.reveal + T.converge;
      it.holder.visible = visible;
      if (!visible) continue;
      const e = easeOutCubic(f);
      const bob = Math.sin(t * 2.2 + it.phase) * 0.006 * f;
      const start = it.rest.clone().add(it.startOff);
      const pos = new THREE.Vector3(lerp(start.x, it.rest.x, e), lerp(start.y, it.rest.y, e) + bob, lerp(start.z, it.rest.z, e));
      let scale = clamp(f * 3) * (1 + 0.18 * Math.sin(Math.PI * clamp(f * 1.4)));        // pops in, tiny overshoot
      let spin = (1 - e) * Math.PI * 4 * it.spinDir + it.tilt * 0.15;
      if (conv > 0) {                                                                      // everything is pulled into the middle of the table
        const ce = easeInCubic(conv);
        pos.lerp(new THREE.Vector3(0, TOP_Y + 0.38, 0), ce);
        scale *= 1 - ce; spin += ce * Math.PI * 5 * it.spinDir;
      }
      it.holder.position.copy(pos); it.holder.rotation.set(it.ing.kind === 'sprite' ? (1 - e) * 1.2 * it.spinDir : 0, spin, 0);
      it.holder.scale.setScalar(Math.max(0.0001, scale));
      // a soft sparkle shimmer on the items while they wait (emissive pulse)
      const m = it.obj.material; if (m && m.emissive && it.ing.kind === 'sprite') m.emissive.setRGB(0.05 + 0.04 * pulse, 0.03 + 0.02 * pulse, 0.015);
    }
    // the legendary
    const tb = tReveal(), lp = clamp((t - tb) / T.legendIn);
    legend.visible = t >= tb - 0.001;
    if (legend.visible) {
      const sc = Math.max(0.0001, easeOutBack(lp));
      const hover = TOP_Y + (C.legendaryHover || 0.34) + Math.sin(t * 1.6) * 0.025 + (1 - easeOutCubic(lp)) * 0.35;
      legend.position.set(0, hover, 0);
      legend.scale.setScalar(sc);
      legend.rotation.set(Math.sin(t * 1.1) * 0.03, (1 - easeOutCubic(lp)) * 1.6 + Math.sin(t * 0.7) * 0.05, Math.sin(t * 0.9) * 0.02);
    }
    legendGlow.visible = legend.visible;
    legendGlow.position.set(0, TOP_Y + (C.legendaryHover || 0.34), 0);
    legendGlowMat.opacity = clamp(lp * 1.2) * (0.26 + 0.1 * Math.sin(t * 2.0)) * (t > tb ? 1 : 0);
    const flashK = t < tb - 0.05 ? 0 : (t < tb ? (t - (tb - 0.05)) / 0.05 : Math.exp(-(t - tb) / 0.09));
    revealLight.intensity = t < tb - 0.05 ? 0 : (0.35 + 4.5 * flashK + 0.08 * Math.sin(t * 2.2)) * smooth(tb - 0.05, tb + 0.15, t);
    // particles
    updateParticles(t);
    // title billboard: above the back of the table, always facing the camera, hidden behind the table / weapon where they overlap
    const tt = t - T.title;
    titleMesh.visible = tt > 0;
    if (titleMesh.visible) {
      drawTitle(tt);
      // anchored in SCREEN space (top-centre, constant size like the reference) but at a depth just BEHIND the table, so the table / weapon can cover part of it
      const fwd = new THREE.Vector3(); camera.getWorldDirection(fwd);
      const upCam = new THREE.Vector3(0, 1, 0).applyQuaternion(camera.quaternion);
      const depth = cam.d + C.titleDepth, halfH = depth * Math.tan(camera.fov * DEG / 2);
      titleMesh.position.copy(camera.position).addScaledVector(fwd, depth).addScaledVector(upCam, halfH * C.titleScreenY);
      titleMesh.quaternion.copy(camera.quaternion);
      const widthWorld = 2 * halfH * camera.aspect * C.titleScreenWidth;
      titleMesh.scale.setScalar(widthWorld / (titleCanvas.width / titleCanvas.height));
    }
    renderer.render(scene, camera);
    return composite(t, flashK);
  }

  // ---------------------------------------------------------------- 2D finish: bloom, vignette, flash, fades
  function composite(t, flashK) {
    ctx.globalCompositeOperation = 'source-over'; ctx.globalAlpha = 1; ctx.filter = 'none';
    ctx.drawImage(glCanvas, 0, 0);
    ctx.save(); ctx.globalCompositeOperation = 'screen';
    ctx.filter = `blur(${Math.round(H * 0.026)}px) brightness(1.2) contrast(1.45)`; ctx.globalAlpha = C.bloom; ctx.drawImage(glCanvas, 0, 0);
    ctx.filter = `blur(${Math.round(H * 0.008)}px) brightness(1.1) contrast(1.3)`; ctx.globalAlpha = C.bloom * 0.45; ctx.drawImage(glCanvas, 0, 0);
    ctx.restore();
    // vignette
    ctx.save(); ctx.filter = 'none';
    const vg = ctx.createRadialGradient(W / 2, H * 0.52, H * 0.25, W / 2, H * 0.52, H * 0.95);
    vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(1, `rgba(0,0,0,${C.vignette})`);
    ctx.fillStyle = vg; ctx.fillRect(0, 0, W, H);
    // flash when the ingredients become the legendary
    if (flashK > 0.001) { ctx.globalCompositeOperation = 'screen'; ctx.fillStyle = `rgba(${C.flashColor},${Math.min(1, flashK * 0.5)})`; ctx.fillRect(0, 0, W, H); }
    // fade in / out
    const fade = Math.min(smooth(0, T.fadeIn, t), 1 - smooth(C.duration - T.fadeOut, C.duration, t));
    if (fade < 0.999) { ctx.globalCompositeOperation = 'source-over'; ctx.fillStyle = `rgba(0,0,0,${1 - fade})`; ctx.fillRect(0, 0, W, H); }
    ctx.restore();
    return outCanvas.toDataURL('image/png');
  }

  // ---------------------------------------------------------------- boot
  async function boot() {
    const keysList = Object.keys(R.images);
    await Promise.all(keysList.map(async (k) => { imgs[k] = await loadImage(R.images[k]); }));
    for (const k of keysList) texs[k] = pixelTex(imgs[k], { mip: k === 'floor', repeat: k === 'floor' ? 60 : 0 });
    texs.floor.repeat.set(C.floorRepeat || 60, C.floorRepeat || 60);
    buildTable(); buildFloor(); buildIngredients(); buildLegend(); buildTitle();
    cloud('embers', 90, 0.05); cloud('landing', R.ingredients.length * 8, 0.045); cloud('burst', 170, 0.085); cloud('spores', 90, 0.055);
    ready = true;
  }
  window.reveal = { ready: () => ready, frame, config: C };
  boot().catch((e) => { window.revealError = String(e && e.stack || e); });
})();
