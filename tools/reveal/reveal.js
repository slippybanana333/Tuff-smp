/* Tuff SMP recipe reveal - a deterministic, frame-by-frame renderer.
 *
 * Remake of the owner's reference clip: the table (here the mod's own SHRINE block) glows on a dark brick floor, the recipe ingredients (one of each)
 * drop in and orbit the crystal, they spiral into it with a quick shine, the legendary appears upright above it, the pixel title drops in behind it.
 * window.reveal.frame(t) renders ONE frame at time t (seconds) and returns it as a PNG data URL; nothing depends on real time, so the Node driver
 * (render_reveal.js) steps through the clip and ffmpeg turns the frames into a video.
 *
 * Everything is driven by window.REVEAL (reveal_data.js, written by tools/make_reveal.py): size, title, theme colours, the Shrine's real model json,
 * the item's real model json, the ingredients (flat sprites or exact vanilla block models) and every texture as a data URL.
 */
(function () {
  'use strict';
  const R = window.REVEAL, C = R.config, T = C.timeline, TH = C.theme;
  const SLOW = C.afterSlow || 1;                                   // after the craft everything runs this much slower
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
  /** flipY=false for everything that goes through Minecraft model uv (v measured from the TOP of the png, like tools/viewer_app.js). */
  function pixelTex(img, opts) {
    const t = new THREE.Texture(img);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = (opts && opts.mip) ? THREE.NearestMipmapLinearFilter : THREE.NearestFilter;
    t.encoding = THREE.sRGBEncoding;
    t.flipY = !(opts && opts.modelUv === true);
    if (opts && opts.repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; }
    t.needsUpdate = true;
    return t;
  }

  // ---------------------------------------------------------------- renderer / scene / lights
  const glCanvas = document.createElement('canvas');
  glCanvas.width = W; glCanvas.height = H;
  const renderer = new THREE.WebGLRenderer({ canvas: glCanvas, antialias: true, preserveDrawingBuffer: true, alpha: false });
  renderer.setSize(W, H, false);
  renderer.outputEncoding = THREE.sRGBEncoding;
  renderer.setClearColor(TH.fog);
  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(TH.fog, 0.045);
  const camera = new THREE.PerspectiveCamera(C.fov || 38, W / H, 0.05, 80);
  const outCanvas = document.createElement('canvas');
  outCanvas.width = W; outCanvas.height = H;
  document.body.appendChild(outCanvas);
  const ctx = outCanvas.getContext('2d');

  const imgs = {}, texs = {};
  let ready = false;
  const CRYSTAL_Y = 1.02;                                              // middle of the Shrine's crystal (model y 14.6..17.2 -> 0.91..1.08)
  const CRYSTAL_TOP = 1.2;

  scene.add(new THREE.AmbientLight(TH.ambient, 1.0));
  const keyLight = new THREE.PointLight(TH.key, TH.keyI, 10, 1.3); keyLight.position.set(-0.9, 2.6, 1.6); scene.add(keyLight);
  const underLight = new THREE.PointLight(TH.under, TH.underI, 5, 1.6); underLight.position.set(0, 0.3, 0); scene.add(underLight);
  const crystalLight = new THREE.PointLight(TH.crystalLight, 1.2, 4.2, 1.5); crystalLight.position.set(0, CRYSTAL_Y + 0.12, 0); scene.add(crystalLight);
  const revealLight = new THREE.PointLight(C.accentLight, 0, 8, 1.3); revealLight.position.set(0, 1.9, 0); scene.add(revealLight);
  const legendLight = new THREE.PointLight(0x9ff8ff, 0, 7, 1.2); legendLight.position.set(0.6, C.legendaryHover + 0.3, 1.4); scene.add(legendLight);   // lights the item's dark handle

  function glowTexture(inner, outer) {
    const c = document.createElement('canvas'); c.width = c.height = 256; const g = c.getContext('2d');
    const gr = g.createRadialGradient(128, 128, 0, 128, 128, 128);
    gr.addColorStop(0, inner); gr.addColorStop(0.35, outer); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 256, 256);
    const t = new THREE.CanvasTexture(c); t.encoding = THREE.sRGBEncoding; return t;
  }
  /** a bright flare: soft core + four thin rays (the "quick shine" when the ingredients become the item) */
  function flareTexture() {
    const c = document.createElement('canvas'); c.width = c.height = 256; const g = c.getContext('2d');
    const gr = g.createRadialGradient(128, 128, 0, 128, 128, 128);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.12, 'rgba(210,255,252,0.85)'); gr.addColorStop(0.4, 'rgba(60,220,235,0.25)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 256, 256);
    g.globalCompositeOperation = 'lighter';
    for (const [x0, y0, x1, y1] of [[8, 128, 248, 128], [128, 8, 128, 248]]) {
      const lg = g.createLinearGradient(x0, y0, x1, y1);
      lg.addColorStop(0, 'rgba(255,255,255,0)'); lg.addColorStop(0.5, 'rgba(255,255,255,0.9)'); lg.addColorStop(1, 'rgba(255,255,255,0)');
      g.strokeStyle = lg; g.lineWidth = 5; g.beginPath(); g.moveTo(x0, y0); g.lineTo(x1, y1); g.stroke();
    }
    const t = new THREE.CanvasTexture(c); t.encoding = THREE.sRGBEncoding; return t;
  }

  // ---------------------------------------------------------------- generic Minecraft block-model json -> three.js
  // default uv when a face has none (same rule as the game)
  const DEFAULT_UV = {
    down: (f, t) => [f[0], 16 - t[2], t[0], 16 - f[2]], up: (f, t) => [f[0], f[2], t[0], t[2]],
    north: (f, t) => [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]], south: (f, t) => [f[0], 16 - t[1], t[0], 16 - f[1]],
    west: (f, t) => [f[2], 16 - t[1], t[2], 16 - f[1]], east: (f, t) => [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
  };
  const FACES = {
    down: [[0, 0, 1], [0, 0, 0], [1, 0, 0], [1, 0, 1]], up: [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]],
    north: [[1, 1, 0], [1, 0, 0], [0, 0, 0], [0, 1, 0]], south: [[0, 1, 1], [0, 0, 1], [1, 0, 1], [1, 1, 1]],
    west: [[0, 1, 0], [0, 0, 0], [0, 0, 1], [0, 1, 1]], east: [[1, 1, 1], [1, 0, 1], [1, 0, 0], [1, 1, 0]],
  };
  function elementMatrix(el) {
    const m = new THREE.Matrix4(); const r = el.rotation; if (!r) return m;
    const o = r.origin, a = (r.angle || 0) * DEG, rm = new THREE.Matrix4();
    if (r.axis === 'x') rm.makeRotationX(a); else if (r.axis === 'y') rm.makeRotationY(a); else rm.makeRotationZ(a);
    return m.makeTranslation(o[0], o[1], o[2]).multiply(rm).multiply(new THREE.Matrix4().makeTranslation(-o[0], -o[1], -o[2]));
  }
  /** keyOf(face) -> texture key; matFor(key) -> material. One mesh per (element, texture). Returns {group, box} in model units. */
  function buildModel(json, keyOf, matFor) {
    const group = new THREE.Group(), box = new THREE.Box3();
    for (const el of json.elements) {
      const rm = elementMatrix(el), buckets = {};
      for (const face of Object.keys(el.faces)) {
        const fd = el.faces[face], key = keyOf(fd), uv = fd.uv || DEFAULT_UV[face](el.from, el.to), cs = FACES[face];
        const b = buckets[key] || (buckets[key] = { pos: [], uvs: [], idx: [], vi: 0 });
        for (let i = 0; i < 4; i++) {
          const c = cs[i];
          const v = new THREE.Vector3(c[0] ? el.to[0] : el.from[0], c[1] ? el.to[1] : el.from[1], c[2] ? el.to[2] : el.from[2]).applyMatrix4(rm);
          b.pos.push(v.x, v.y, v.z); box.expandByPoint(v);
          b.uvs.push(((i === 0 || i === 1) ? uv[0] : uv[2]) / 16, ((i === 0 || i === 3) ? uv[1] : uv[3]) / 16);
        }
        b.idx.push(b.vi, b.vi + 1, b.vi + 2, b.vi, b.vi + 2, b.vi + 3); b.vi += 4;
      }
      for (const key of Object.keys(buckets)) {
        const b = buckets[key], geo = new THREE.BufferGeometry();
        geo.setAttribute('position', new THREE.Float32BufferAttribute(b.pos, 3));
        geo.setAttribute('uv', new THREE.Float32BufferAttribute(b.uvs, 2));
        geo.setIndex(b.idx); geo.computeVertexNormals();
        group.add(new THREE.Mesh(geo, matFor(key)));
      }
    }
    return { group, box };
  }
  const matShrine = (k) => new THREE.MeshLambertMaterial({ map: texs[k], alphaTest: 0.5, emissive: 0x9aa4ac, emissiveMap: texs[k], emissiveIntensity: 0.42 });
  const matMace = (k) => new THREE.MeshLambertMaterial({ map: texs[k], alphaTest: 0.5, color: 0x748C8E, emissive: C.legendaryEmissive, emissiveIntensity: 1.0 });   // constant lift (no map) so the dark handle stays readable
  const matBlock = (k) => new THREE.MeshLambertMaterial({ map: texs[k], alphaTest: 0.1, emissive: 0x909aa0, emissiveMap: texs[k], emissiveIntensity: 0.5 });

  // ---------------------------------------------------------------- the Shrine (+ a wavy reflection in the floor)
  const waveU = { time: { value: 0 }, amp: { value: 0.5 } };
  function wavy(mat) {
    mat.side = THREE.DoubleSide;
    mat.onBeforeCompile = (sh) => {
      sh.uniforms.uWaveT = waveU.time; sh.uniforms.uWaveA = waveU.amp;
      sh.vertexShader = 'uniform float uWaveT;\nuniform float uWaveA;\n' + sh.vertexShader.replace('#include <begin_vertex>',
        '#include <begin_vertex>\n transformed.x += sin(transformed.y * 2.2 + uWaveT * 3.0) * uWaveA; transformed.z += cos(transformed.y * 1.9 + uWaveT * 2.3) * uWaveA * 0.6;');
    };
    return mat;
  }
  function buildShrine() {
    const real = buildModel(R.shrine, () => 'shrine', matShrine);
    const holder = new THREE.Group(); real.group.position.set(-8, 0, -8); holder.add(real.group); holder.scale.setScalar(1 / 16); scene.add(holder);
    const refl = buildModel(R.shrine, () => 'shrine', (k) => wavy(matShrine(k)));
    const rh = new THREE.Group(); refl.group.position.set(-8, 0, -8); rh.add(refl.group); rh.scale.set(1 / 16, -1 / 16, 1 / 16); scene.add(rh);
  }

  let floorGlow, floorGlow2, crystalGlow, crystalGlowMat;
  function buildFloor() {
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(60, 60),
      new THREE.MeshLambertMaterial({ map: texs.floor, color: TH.floorColor, emissive: TH.floorEmissive, emissiveMap: texs.floor, transparent: true, opacity: 0.8 }));
    floor.rotation.x = -Math.PI / 2; floor.renderOrder = 2; scene.add(floor);
    const gm = new THREE.MeshBasicMaterial({ map: glowTexture(TH.glow1[0], TH.glow1[1]), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
    floorGlow = new THREE.Mesh(new THREE.PlaneGeometry(5.6, 5.6), gm); floorGlow.rotation.x = -Math.PI / 2; floorGlow.position.y = 0.012; floorGlow.renderOrder = 3; scene.add(floorGlow);
    const gm2 = new THREE.MeshBasicMaterial({ map: glowTexture(TH.glow2[0], TH.glow2[1]), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
    floorGlow2 = new THREE.Mesh(new THREE.PlaneGeometry(15, 15), gm2); floorGlow2.rotation.x = -Math.PI / 2; floorGlow2.position.y = 0.014; floorGlow2.renderOrder = 3; scene.add(floorGlow2);
    // a gold halo round the Shrine's crystal
    crystalGlowMat = new THREE.SpriteMaterial({ map: glowTexture('rgba(255,225,120,0.95)', 'rgba(255,170,40,0.35)'), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
    crystalGlow = new THREE.Sprite(crystalGlowMat); crystalGlow.scale.set(1.25, 1.25, 1); crystalGlow.position.set(0, CRYSTAL_Y + 0.1, 0); scene.add(crystalGlow);
  }

  // ---------------------------------------------------------------- ingredients: flat items = pixel-extruded sprites, blocks = their exact vanilla models
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
      const o = (j * w + i) * 4, c3 = [toLinear(d[o] / 255), toLinear(d[o + 1] / 255), toLinear(d[o + 2] / 255)];
      const x0 = (i - w / 2) * px, x1 = x0 + px, z0 = (j - h / 2) * px, z1 = z0 + px;
      quad([[x0, yt, z0], [x0, yt, z1], [x1, yt, z1], [x1, yt, z0]], c3, 1.0);
      quad([[x0, yb, z0], [x1, yb, z0], [x1, yb, z1], [x0, yb, z1]], c3, 0.5);
      if (alpha(i - 1, j) < 20) quad([[x0, yt, z1], [x0, yt, z0], [x0, yb, z0], [x0, yb, z1]], c3, 0.66);
      if (alpha(i + 1, j) < 20) quad([[x1, yt, z0], [x1, yt, z1], [x1, yb, z1], [x1, yb, z0]], c3, 0.66);
      if (alpha(i, j - 1) < 20) quad([[x0, yt, z0], [x1, yt, z0], [x1, yb, z0], [x0, yb, z0]], c3, 0.78);
      if (alpha(i, j + 1) < 20) quad([[x1, yt, z1], [x0, yt, z1], [x0, yb, z1], [x1, yb, z1]], c3, 0.78);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
    g.setIndex(idx); g.computeVertexNormals();
    return g;
  }
  function buildIngredients() {
    const N = R.ingredients.length;
    R.ingredients.forEach((ing, i) => {
      const inner = new THREE.Group();
      if (ing.kind === 'sprite') {
        // unlit (true colours); stood upright so it reads from the camera
        const mesh = new THREE.Mesh(spriteGeometry(imgs[ing.img], C.itemSize * 1.05, 0.034), new THREE.MeshBasicMaterial({ vertexColors: true, side: THREE.DoubleSide }));
        mesh.rotation.x = Math.PI / 2; inner.add(mesh);
      } else {
        const m = buildModel(ing.model, (f) => f.texture, matBlock);
        const c = new THREE.Vector3(), sz = new THREE.Vector3(); m.box.getCenter(c); m.box.getSize(sz);
        m.group.position.set(-c.x, -c.y, -c.z);
        inner.add(m.group); inner.scale.setScalar(C.itemSize / Math.max(sz.x, sz.y, sz.z));
      }
      const holder = new THREE.Group(); holder.add(inner); scene.add(holder);
      const rnd = rng(4321 + i * 91);
      items.push({ holder, inner, ing, i, N, baseAngle: (i / N) * Math.PI * 2, phase: rnd() * 6.28, spinDir: rnd() < 0.5 ? -1 : 1,
        startOff: new THREE.Vector3((rnd() - 0.5) * 0.6, 1.5 + rnd() * 0.8, (rnd() - 0.5) * 0.6), spawn: T.itemsStart + i * T.stagger, arrive: T.itemsStart + i * T.stagger + T.fall });
    });
  }
  const ringPos = (it, t) => {
    const a = it.baseAngle + t * C.ringSpeed;
    return new THREE.Vector3(Math.cos(a) * C.ringRadius, C.ringY + Math.sin(t * 1.7 + it.phase) * 0.035, Math.sin(a) * C.ringRadius);
  };

  // ---------------------------------------------------------------- the legendary (its real model, standing upright above the crystal)
  let legend, legendInner, legendGlow, legendGlowMat, shine, shineMat;
  function buildLegend() {
    const m = buildModel(R.model.json, () => 'model', matMace);
    const c = new THREE.Vector3(), sz = new THREE.Vector3(); m.box.getCenter(c); m.box.getSize(sz);
    m.group.position.set(-c.x, -c.y, -c.z);
    legendInner = new THREE.Group(); legendInner.add(m.group);
    legendInner.scale.setScalar(C.legendaryLength / Math.max(sz.x, sz.y, sz.z));
    legend = new THREE.Group(); legend.add(legendInner); scene.add(legend);
    legendGlowMat = new THREE.SpriteMaterial({ map: glowTexture(C.legendaryGlow[0], C.legendaryGlow[1]), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
    legendGlow = new THREE.Sprite(legendGlowMat); legendGlow.scale.set(2.6, 2.6, 1); scene.add(legendGlow);
    shineMat = new THREE.SpriteMaterial({ map: flareTexture(), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false, fog: false });
    shine = new THREE.Sprite(shineMat); shine.renderOrder = 9; scene.add(shine);
  }

  // ---------------------------------------------------------------- particles (analytic, so any frame renders on its own)
  function starTexture() {
    const c = document.createElement('canvas'); c.width = c.height = 64; const g = c.getContext('2d');
    const gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.25, 'rgba(255,255,255,0.7)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
    g.strokeStyle = 'rgba(255,255,255,0.85)'; g.lineWidth = 2; g.beginPath(); g.moveTo(32, 4); g.lineTo(32, 60); g.moveTo(4, 32); g.lineTo(60, 32); g.stroke();
    return new THREE.CanvasTexture(c);
  }
  const clouds = {}; let starTex;
  function cloud(name, n, size) {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(new Float32Array(n * 3), 3));
    geo.setAttribute('color', new THREE.Float32BufferAttribute(new Float32Array(n * 3), 3));
    const pts = new THREE.Points(geo, new THREE.PointsMaterial({ size, map: starTex, vertexColors: true, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true, fog: false }));
    pts.frustumCulled = false; scene.add(pts);
    const seeds = [], r = rng(name.length * 991 + n);
    for (let i = 0; i < n; i++) seeds.push({ a: r(), b: r(), c: r(), d: r(), e: r() });
    clouds[name] = { geo, n, seeds, pts };
  }
  function setPoint(cl, i, x, y, z, r, g, b, a) { const p = cl.geo.attributes.position.array, c = cl.geo.attributes.color.array; p[i * 3] = x; p[i * 3 + 1] = y; p[i * 3 + 2] = z; c[i * 3] = r * a; c[i * 3 + 1] = g * a; c[i * 3 + 2] = b * a; }
  const dirty = (cl) => { cl.geo.attributes.position.needsUpdate = true; cl.geo.attributes.color.needsUpdate = true; };

  function updateParticles(t, itemPos) {
    const em = TH.ember, acc = em;
    // 1) motes rising round the Shrine
    let cl = clouds.embers;
    for (let i = 0; i < cl.n; i++) {
      const s = cl.seeds[i], life = 4.0, age = ((t * 0.5 + s.a * life) % life), k = age / life;
      const x = (s.b - 0.5) * 1.5 + Math.sin(age * 1.5 + s.c * 6) * 0.1, z = (s.d - 0.5) * 1.5 + Math.cos(age * 1.2 + s.e * 6) * 0.1, y = 0.2 + age * (0.45 + s.c * 0.3);
      const a = Math.sin(Math.PI * k) * (0.5 + 0.5 * Math.sin(t * 8 + s.e * 20)) * 0.9;
      setPoint(cl, i, x, y, z, em[0], em[1], em[2], Math.max(0, a));
    }
    dirty(cl);
    // 2) a puff where each ingredient arrives in the ring
    cl = clouds.landing; let n = 0;
    for (const it of items) {
      const p0 = ringPos(it, it.arrive);
      for (let j = 0; j < 8; j++, n++) {
        const age = t - it.arrive, s = cl.seeds[n], ang = (j / 8) * Math.PI * 2 + s.a;
        if (age < 0 || age > 0.7) { setPoint(cl, n, 0, -9, 0, 0, 0, 0, 0); continue; }
        const rr = 0.06 + age * (0.5 + s.b * 0.25), k = age / 0.7;
        setPoint(cl, n, p0.x + Math.cos(ang) * rr, p0.y + Math.sin(ang) * rr * 0.8, p0.z, 0.85, 1.0, 1.0, (1 - k) * 0.95);
      }
    }
    dirty(cl);
    // 3) glints that twinkle on the ingredients (the reference's items have a shine)
    cl = clouds.glints; n = 0;
    for (const it of items) {
      const p = itemPos[it.i];
      for (let j = 0; j < 4; j++, n++) {
        const s = cl.seeds[n];
        if (!p) { setPoint(cl, n, 0, -9, 0, 0, 0, 0, 0); continue; }
        const tw = Math.pow(Math.max(0, Math.sin(t * (3.5 + s.a * 3) + s.b * 20)), 6);
        setPoint(cl, n, p.x + (s.c - 0.5) * C.itemSize * 1.1, p.y + (s.d - 0.5) * C.itemSize * 1.1, p.z + (s.e - 0.5) * C.itemSize * 0.6, 1, 1, 1, tw);
      }
    }
    dirty(cl);
    // 4) burst out of the crystal when the item appears
    cl = clouds.burst; const tb = T.reveal + T.converge;
    for (let i = 0; i < cl.n; i++) {
      const s = cl.seeds[i], age = (t - tb) / SLOW;
      if (age < 0 || age > 2.2) { setPoint(cl, i, 0, -9, 0, 0, 0, 0, 0); continue; }
      const th = s.a * Math.PI * 2, ph = Math.acos(2 * s.b - 1), sp = 0.8 + s.c * 2.4, drag = 1 - Math.exp(-age * 2.2);
      const dx = Math.sin(ph) * Math.cos(th), dy = Math.cos(ph) * 0.9 + 0.35, dz = Math.sin(ph) * Math.sin(th), k = age / 2.2;
      setPoint(cl, i, dx * sp * drag * 0.6, CRYSTAL_TOP + 0.1 + dy * sp * drag * 0.6 - 0.1 * age * age, dz * sp * drag * 0.6, lerp(1, acc[0], s.d), lerp(1, acc[1], s.d), lerp(1, acc[2], s.d), Math.pow(1 - k, 1.5));
    }
    dirty(cl);
    // 5) spores orbiting the item afterwards
    cl = clouds.spores;
    for (let i = 0; i < cl.n; i++) {
      const s = cl.seeds[i], tt = (t - (tb + 0.2 * SLOW)) / SLOW;
      if (tt < 0) { setPoint(cl, i, 0, -9, 0, 0, 0, 0, 0); continue; }
      const life = 3.2, age = (tt * 0.5 + s.a * life) % life, k = age / life, ang = s.b * 6.28 + age * 0.5, rad = 0.35 + s.c * 0.6;
      setPoint(cl, i, Math.cos(ang) * rad, CRYSTAL_TOP + 0.1 + age * 0.55, Math.sin(ang) * rad, acc[0], acc[1], acc[2], Math.sin(Math.PI * k) * 0.8 * smooth(0, 0.8, tt));
    }
    dirty(cl);
  }

  // ---------------------------------------------------------------- title (Minecraft font, bold, as a billboard BEHIND the Shrine / item)
  let titleCanvas, titleCtx, titleTex, titleMesh, glyphW = {};
  const FONT_S = 14;
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
    let total = 0; for (const ch of C.title) total += (glyphW[ch.charCodeAt(0)] || 5) + 2;     // +1 spacing, +1 for the bold smear
    total -= 2;
    titleCanvas = document.createElement('canvas'); titleCanvas.width = (total + 5) * FONT_S; titleCanvas.height = 12 * FONT_S;
    titleCtx = titleCanvas.getContext('2d');
    titleTex = new THREE.CanvasTexture(titleCanvas); titleTex.encoding = THREE.sRGBEncoding; titleTex.minFilter = THREE.LinearFilter;
    titleMesh = new THREE.Mesh(new THREE.PlaneGeometry(titleCanvas.width / titleCanvas.height, 1), new THREE.MeshBasicMaterial({ map: titleTex, transparent: true, depthWrite: false, fog: false, toneMapped: false }));
    titleMesh.renderOrder = 6; titleMesh.visible = false; scene.add(titleMesh);
    titleMesh.userData.total = total;
  }
  function drawTitle(tt) {
    const g = titleCtx, text = C.title, total = titleMesh.userData.total, col = C.titleColors;
    g.clearRect(0, 0, titleCanvas.width, titleCanvas.height);
    let x = 2.5; const baseY = 2 * FONT_S, r = rng(9);
    for (let i = 0; i < text.length; i++) {
      const code = text.charCodeAt(i), gw = glyphW[code] || 5;
      const p = clamp((tt - i * T.letterStagger) / T.letterTime);
      if (code !== 32 && p > 0) {
        const f = (x - 2 + gw / 2) / total;
        const mix = (a, b, k) => a.map((v, j) => Math.round(lerp(v, b[j], k)));
        const rgb = f < 0.5 ? mix(col[0], col[1], f * 2) : mix(col[1], col[2], (f - 0.5) * 2);
        const lift = (1 - easeOutBack(clamp(p))) * -5 * FONT_S;
        const jx = p < 1 ? (r() - 0.5) * 0.7 * FONT_S : 0, jy = p < 1 ? (r() - 0.5) * 0.5 * FONT_S : 0;
        const px = x * FONT_S + jx, py = baseY + lift + jy;
        g.globalAlpha = clamp(p * 2.2);
        const dark = glyphCanvas(code, '#08081e'), mid = glyphCanvas(code, `rgb(${Math.round(rgb[0] * 0.33)},${Math.round(rgb[1] * 0.33)},${Math.round(rgb[2] * 0.5)})`), body = glyphCanvas(code, `rgb(${rgb[0]},${rgb[1]},${rgb[2]})`), hi = glyphCanvas(code, '#ffffff');
        const o = FONT_S * 0.95;
        for (const bx of [0, FONT_S]) {                                // bold = the glyph drawn twice, one font pixel apart
          for (const [dx, dy] of [[-o, 0], [o, 0], [0, -o], [0, o], [-o, -o], [o, -o], [-o, o], [o, o]]) g.drawImage(dark, px + bx + dx, py + dy);
        }
        for (const bx of [0, FONT_S]) g.drawImage(mid, px + bx + FONT_S, py + FONT_S);
        for (const bx of [0, FONT_S]) g.drawImage(body, px + bx, py);
        g.save(); g.beginPath(); g.rect(px, py, 10 * FONT_S, 3.2 * FONT_S); g.clip(); g.globalAlpha *= 0.55;
        for (const bx of [0, FONT_S]) g.drawImage(hi, px + bx, py - FONT_S * 0.4);
        g.restore();
        if (p < 1) { g.globalAlpha = (1 - p) * 0.9; for (const bx of [0, FONT_S]) g.drawImage(hi, px + bx, py); }
      }
      x += gw + 2;
    }
    g.globalAlpha = 1; titleTex.needsUpdate = true;
  }

  // ---------------------------------------------------------------- camera
  const camState = { d: 5 };
  function placeCamera(t) {
    const dbg = C.debugCam;
    const d = dbg ? dbg[0] : keys(t, T.camDist), pitch = (dbg ? dbg[1] : keys(t, T.camPitch)) * DEG, yaw = (dbg ? dbg[2] : keys(t, T.camYaw)) * DEG;
    const ty = dbg && dbg[3] !== undefined ? dbg[3] : keys(t, T.camTargetY);
    const shake = 0.0025 * Math.sin(t * 37) + 0.0015 * Math.sin(t * 53 + 1);
    camera.position.set(d * Math.cos(pitch) * Math.sin(yaw) + shake, ty + d * Math.sin(pitch) + shake, d * Math.cos(pitch) * Math.cos(yaw));
    camera.lookAt(0, ty, 0); camera.updateMatrixWorld(); camState.d = d;
  }

  // ---------------------------------------------------------------- one frame
  const tReveal = () => T.reveal + T.converge;
  function frame(t) {
    placeCamera(t);
    const pulse = 0.5 + 0.5 * Math.sin(t * 2.4);
    keyLight.intensity = TH.keyI + 0.12 * pulse;
    crystalLight.intensity = 1.1 + 0.25 * pulse + 0.6 * smooth(tReveal() - 0.3, tReveal(), t) * (1 - smooth(tReveal() + 0.1 * SLOW, tReveal() + 0.8 * SLOW, t));
    crystalGlowMat.opacity = 0.5 + 0.2 * pulse;
    floorGlow.material.opacity = 0.85 + 0.15 * pulse; floorGlow.scale.setScalar(1 + 0.03 * pulse);
    // reflection ripples strongly in the wide opening, then calms down as the camera rises
    waveU.time.value = t; waveU.amp.value = 0.12 + 0.5 * (1 - smooth(0.4, 3.0, t));

    // ingredients
    const itemPos = [];
    for (const it of items) {
      const age = t - it.spawn, f = clamp(age / T.fall), conv = clamp((t - T.reveal) / T.converge);
      const visible = age > 0 && t < tReveal();
      it.holder.visible = visible; itemPos[it.i] = null;
      if (!visible) continue;
      const e = easeOutCubic(f), rp = ringPos(it, t);
      const pos = rp.clone().add(it.startOff.clone().multiplyScalar(1 - e));
      let scale = clamp(f * 3) * (1 + 0.2 * Math.sin(Math.PI * clamp(f * 1.4)));
      let extraSpin = (1 - e) * Math.PI * 3 * it.spinDir;
      if (conv > 0) {                                                       // spiral into the crystal
        const ce = easeInCubic(conv), a = it.baseAngle + t * C.ringSpeed + ce * 5.5, rr = C.ringRadius * (1 - ce);
        pos.set(Math.cos(a) * rr, lerp(C.ringY, CRYSTAL_TOP, ce), Math.sin(a) * rr);
        scale *= 1 - ce; extraSpin += ce * Math.PI * 4 * it.spinDir;
      }
      it.holder.position.copy(pos); itemPos[it.i] = pos;
      if (it.ing.kind === 'sprite') {
        it.holder.rotation.set(0, Math.atan2(camera.position.x - pos.x, camera.position.z - pos.z) + extraSpin * 0.35, Math.sin(t * 1.3 + it.phase) * 0.06);
      } else {
        it.holder.rotation.set(0.32, t * 0.8 * it.spinDir + it.phase + extraSpin, 0);
      }
      it.holder.scale.setScalar(Math.max(0.0001, scale * (1 + 0.04 * Math.sin(t * 3 + it.phase))));
    }

    // the legendary
    const tb = tReveal(), lp = clamp((t - tb) / T.legendIn), after = t - tb;
    legend.visible = t >= tb - 0.001;
    const hover = C.legendaryHover;
    if (legend.visible) {
      legend.position.set(0, hover + Math.sin(t * 1.5) * 0.03 - (1 - easeOutCubic(lp)) * 0.7, 0);
      legend.scale.setScalar(Math.max(0.0001, easeOutBack(lp)));
      legend.rotation.set(Math.sin(t * 1.1) * 0.03, C.legendarySpin * after + (1 - easeOutCubic(lp)) * 7.0 + 0.5, Math.sin(t * 0.9) * 0.025);
    }
    legendGlow.visible = legend.visible; legendGlow.position.set(0, hover, 0);
    legendGlowMat.opacity = clamp(lp * 1.2) * (0.14 + 0.06 * Math.sin(t * 2.0)) * (t > tb ? 1 : 0);
    // quick shine (instead of a full white flash): flare at the crystal + a short light burst
    const sa = t - (tb - 0.04), sk = sa < 0 ? 0 : clamp(sa / (0.6 * SLOW));
    shine.visible = sa > 0 && sa < 0.62 * SLOW; shine.position.set(0, CRYSTAL_TOP, 0);
    shine.scale.setScalar(0.5 + 3.4 * easeOutCubic(sk)); shineMat.opacity = (1 - smooth(0.12, 1.0, sk)) * 0.95;
    const flashK = sa < 0 ? 0 : Math.exp(-sa / (0.12 * SLOW));
    revealLight.intensity = t < tb - 0.05 ? 0 : (0.4 + 4.0 * flashK + 0.08 * Math.sin(t * 2.2)) * smooth(tb - 0.05, tb + 0.15 * SLOW, t);

    legendLight.intensity = 0.9 * smooth(tb, tb + 0.5 * SLOW, t);
    updateParticles(t, itemPos);

    // title billboard: screen-anchored at the top, constant size, at a depth BEHIND the Shrine so it (and the item) can cover part of it
    const tt = t - T.title;
    titleMesh.visible = tt > 0;
    if (titleMesh.visible) {
      drawTitle(tt);
      const fwd = new THREE.Vector3(); camera.getWorldDirection(fwd);
      const upCam = new THREE.Vector3(0, 1, 0).applyQuaternion(camera.quaternion);
      const depth = camState.d + C.titleDepth, halfH = depth * Math.tan(camera.fov * DEG / 2);
      titleMesh.position.copy(camera.position).addScaledVector(fwd, depth).addScaledVector(upCam, halfH * C.titleScreenY);
      titleMesh.quaternion.copy(camera.quaternion);
      titleMesh.scale.setScalar(2 * halfH * camera.aspect * C.titleScreenWidth / (titleCanvas.width / titleCanvas.height));
    }
    renderer.render(scene, camera);
    return composite(t, flashK);
  }

  // ---------------------------------------------------------------- 2D finish: bloom, vignette, fades
  function composite(t, flashK) {
    ctx.globalCompositeOperation = 'source-over'; ctx.globalAlpha = 1; ctx.filter = 'none';
    ctx.drawImage(glCanvas, 0, 0);
    ctx.save(); ctx.globalCompositeOperation = 'screen';
    ctx.filter = `blur(${Math.round(H * 0.026)}px) brightness(1.2) contrast(1.45)`; ctx.globalAlpha = C.bloom; ctx.drawImage(glCanvas, 0, 0);
    ctx.filter = `blur(${Math.round(H * 0.008)}px) brightness(1.1) contrast(1.3)`; ctx.globalAlpha = C.bloom * 0.45; ctx.drawImage(glCanvas, 0, 0);
    ctx.restore();
    ctx.save(); ctx.filter = 'none';
    const vg = ctx.createRadialGradient(W / 2, H * 0.52, H * 0.25, W / 2, H * 0.52, H * 0.95);
    vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(1, `rgba(0,0,0,${C.vignette})`);
    ctx.fillStyle = vg; ctx.fillRect(0, 0, W, H);
    if (flashK > 0.001) { ctx.globalCompositeOperation = 'screen'; ctx.fillStyle = `rgba(${C.flashColor},${Math.min(0.16, flashK * 0.16)})`; ctx.fillRect(0, 0, W, H); }   // just a hint of shine
    const fade = Math.min(smooth(0, T.fadeIn, t), 1 - smooth(C.duration - T.fadeOut, C.duration, t));
    if (fade < 0.999) { ctx.globalCompositeOperation = 'source-over'; ctx.fillStyle = `rgba(0,0,0,${1 - fade})`; ctx.fillRect(0, 0, W, H); }
    ctx.restore();
    return outCanvas.toDataURL('image/png');
  }

  // ---------------------------------------------------------------- boot
  async function boot() {
    const names = Object.keys(R.images);
    await Promise.all(names.map(async (k) => { imgs[k] = await loadImage(R.images[k]); }));
    for (const k of names) {
      if (k === 'font' || k.startsWith('ing_')) continue;
      const isModel = k === 'model' || k === 'shrine' || k.startsWith('blk_');
      texs[k] = pixelTex(imgs[k], { mip: k === 'floor', repeat: k === 'floor', modelUv: isModel });
    }
    texs.floor.repeat.set(C.floorRepeat || 60, C.floorRepeat || 60);
    starTex = starTexture();
    buildShrine(); buildFloor(); buildIngredients(); buildLegend(); buildTitle();
    cloud('embers', 110, 0.05); cloud('landing', R.ingredients.length * 8, 0.05); cloud('glints', R.ingredients.length * 4, 0.075); cloud('burst', 180, 0.085); cloud('spores', 100, 0.055);
    ready = true;
  }
  window.reveal = { ready: () => ready, frame, config: C };
  boot().catch((e) => { window.revealError = String(e && e.stack || e); });
})();
