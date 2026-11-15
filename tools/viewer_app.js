/* Tuff SMP model viewer - application code.  Inlined into viewer/index.html by tools/build_viewer.py.
 * Everything here mirrors what Minecraft 1.21.11 really does (see tools/common.py for the sources). Units: 1 unit = 1 model pixel (16 = 1 block). */
(function () {
'use strict';
const D = window.VIEWER_DATA;
const $ = (id) => document.getElementById(id);
const DEG = Math.PI / 180;

// ------------------------------------------------------------------ matrices
const M4 = () => new THREE.Matrix4();
const T = (x, y, z) => M4().makeTranslation(x, y, z);
const S = (x, y, z) => M4().makeScale(x, y === undefined ? x : y, z === undefined ? x : z);
const RX = (d) => M4().makeRotationX(d * DEG);
const RY = (d) => M4().makeRotationY(d * DEG);
const RZ = (d) => M4().makeRotationZ(d * DEG);
const mul = (...ms) => ms.reduce((a, b) => a.clone().multiply(b));
// Minecraft Quaternionf.rotationXYZ(x,y,z)  => matrix Rx * Ry * Rz
const rotXYZ = (r) => mul(RX(r[0]), RY(r[1]), RZ(r[2]));
function displayMatrix(d, left) {
  let t = d.translation || [0, 0, 0], r = d.rotation || [0, 0, 0], s = d.scale || [1, 1, 1];
  if (left) { t = [-t[0], t[1], t[2]]; r = [r[0], -r[1], -r[2]]; }
  return mul(T(t[0], t[1], t[2]), rotXYZ(r), S(s[0], s[1], s[2]), T(-8, -8, -8));
}
const E2W = mul(S(0.9375, -0.9375, -0.9375), T(0, -24.016, 0));          // entity model space -> world (yaw 0, player faces +z)
const ARM_PITCH = -18;                                                    // arm pose while holding an item
const thirdChain = () => mul(T(-5, 2, 0), RX(ARM_PITCH), RX(-90), RY(180), T(1, 2, -10));

// ------------------------------------------------------------------ textures
const texCache = {};
function getTexture(url) {
  if (texCache[url]) return texCache[url];
  const img = new Image();
  const tex = new THREE.Texture(img);
  tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter; tex.generateMipmaps = false;
  tex.flipY = false; tex.encoding = THREE.sRGBEncoding;
  img.onload = () => { tex.needsUpdate = true; tex.userData.w = img.width; tex.userData.h = img.height; requestRender(); };
  img.src = url;
  texCache[url] = tex;
  return tex;
}

// ------------------------------------------------------------------ element geometry (block-model json)
// CubeFace corner order (verified against the game code): each entry = [xSide, ySide, zSide] with 0=min 1=max
const CUBE_FACE = {
  down:  [[0,0,1],[0,0,0],[1,0,0],[1,0,1]],
  up:    [[0,1,0],[0,1,1],[1,1,1],[1,1,0]],
  north: [[1,1,0],[1,0,0],[0,0,0],[0,1,0]],
  south: [[0,1,1],[0,0,1],[1,0,1],[1,1,1]],
  west:  [[0,1,0],[0,0,0],[0,0,1],[0,1,1]],
  east:  [[1,1,1],[1,0,1],[1,0,0],[1,1,0]],
};
function elementMatrix(el) {
  const r = el.rotation;
  if (!r) return M4();
  const o = r.origin;
  let m;
  if (r.axis) m = ({x: RX, y: RY, z: RZ})[r.axis](r.angle || 0);
  else m = mul(RZ(r.z || 0), RY(r.y || 0), RX(r.x || 0));
  return mul(T(o[0], o[1], o[2]), m, T(-o[0], -o[1], -o[2]));
}
function elementGeometry(model, el) {
  const pos = [], uvs = [], idx = [];
  const [tw, th] = model.texture_size || [16, 16];
  const f = el.from, t = el.to;
  const rm = elementMatrix(el);
  let vi = 0;
  for (const face of Object.keys(el.faces)) {
    const fd = el.faces[face];
    const uv = fd.uv || [0, 0, 16, 16];
    const corners = CUBE_FACE[face];
    for (let i = 0; i < 4; i++) {
      const c = corners[i];
      const v = new THREE.Vector3(c[0] ? t[0] : f[0], c[1] ? t[1] : f[1], c[2] ? t[2] : f[2]).applyMatrix4(rm);
      pos.push(v.x, v.y, v.z);
      const u = (i === 0 || i === 1) ? uv[0] : uv[2];
      const vv = (i === 0 || i === 3) ? uv[1] : uv[3];
      uvs.push(u / 16, vv / 16);          // flipY=false: v measured from the top, like the png
    }
    idx.push(vi, vi + 1, vi + 2, vi, vi + 2, vi + 3);
    vi += 4;
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  g.setIndex(idx);
  g.computeVertexNormals();
  return g;
}
function hashColor(name) {
  let h = 2166136261;
  for (let i = 0; i < name.length; i++) { h ^= name.charCodeAt(i); h = Math.imul(h, 16777619); }
  const hue = ((h >>> 0) % 360) / 360;
  return new THREE.Color().setHSL(hue, 0.55, 0.55);
}

// ------------------------------------------------------------------ model -> THREE.Group
let renderMode = 'textures';
let selectedElement = null;
const pickables = [];

function buildModelGroup(modelName, opts) {
  opts = opts || {};
  const entry = D.models[modelName];
  const model = entry.json;
  const group = new THREE.Group();
  const texUrl = D.textures[entry.texture];
  const tex = texUrl ? getTexture(texUrl) : null;
  model.elements.forEach((el, index) => {
    const geo = elementGeometry(model, el);
    let mat;
    if (renderMode === 'wireframe') mat = new THREE.MeshBasicMaterial({ color: hashColor(el.name), wireframe: true });
    else if (renderMode === 'flat') mat = new THREE.MeshLambertMaterial({ color: hashColor(el.name) });
    else mat = new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.5, side: THREE.FrontSide });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.userData = { element: el, index, model: modelName };
    group.add(mesh);
    if (opts.pickable) pickables.push(mesh);
    if (selectedElement && opts.pickable && selectedElement.model === modelName && selectedElement.name === el.name) {
      const box = new THREE.Box3().setFromObject(mesh);
      const helper = new THREE.Box3Helper(box, 0xffff00);
      group.add(helper);
      mat.emissive = new THREE.Color(0x553300);
    }
  });
  group.userData.model = model;
  return group;
}
function modelBounds(model) {
  const box = new THREE.Box3();
  model.elements.forEach((el) => {
    const rm = elementMatrix(el);
    for (const x of [el.from[0], el.to[0]]) for (const y of [el.from[1], el.to[1]]) for (const z of [el.from[2], el.to[2]])
      box.expandByPoint(new THREE.Vector3(x, y, z).applyMatrix4(rm));
  });
  return box;
}

// ------------------------------------------------------------------ entity boxes (player/armor/elytra) - vanilla ModelPart.Cuboid
function boxFaces(b) {
  // b: {pos:[x,y,z], size:[sx,sy,sz], uv:[u,v], extra, mirror}
  const [x, y, z] = b.pos, [sx, sy, sz] = b.size, e = b.extra || 0;
  let x0 = x - e, x1 = x + sx + e;
  const y0 = y - e, y1 = y + sy + e, z0 = z - e, z1 = z + sz + e;
  if (b.mirror) { const t = x1; x1 = x0; x0 = t; }
  const V = {
    1: [x0, y0, z0], 2: [x1, y0, z0], 3: [x1, y1, z0], 4: [x0, y1, z0],
    5: [x0, y0, z1], 6: [x1, y0, z1], 7: [x1, y1, z1], 8: [x0, y1, z1],
  };
  const u = b.uv[0], v = b.uv[1];
  const j = u, k = u + sz, l = u + sz + sx, m = u + sz + 2 * sx, n = u + 2 * sz + sx, o = u + 2 * sz + 2 * sx;
  const p = v, q = v + sz, r = v + sz + sy;
  const faces = [
    [[6, 5, 1, 2], k, p, l, q],   // down (smallest y)
    [[3, 4, 8, 7], l, q, m, p],   // up
    [[1, 5, 8, 4], j, q, k, r],   // west
    [[2, 1, 4, 3], k, q, l, r],   // north
    [[6, 2, 3, 7], l, q, n, r],   // east
    [[5, 6, 7, 8], n, q, o, r],   // south
  ];
  return faces.map(([vs, u1, v1, u2, v2]) => {
    const uvs = [[u2, v1], [u1, v1], [u1, v2], [u2, v2]];
    let verts = vs.map((id, i) => ({ p: V[id], uv: uvs[i] }));
    if (b.mirror) verts = verts.reverse();
    return verts;
  });
}
function boxMesh(b, tex, tw, th, color) {
  const pos = [], uvs = [], idx = [];
  let vi = 0;
  for (const face of boxFaces(b)) {
    for (const vt of face) { pos.push(vt.p[0], vt.p[1], vt.p[2]); uvs.push(vt.uv[0] / tw, vt.uv[1] / th); }
    idx.push(vi, vi + 1, vi + 2, vi, vi + 2, vi + 3);
    vi += 4;
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  g.setIndex(idx); g.computeVertexNormals();
  const mat = renderMode === 'wireframe' ? new THREE.MeshBasicMaterial({ color: color || 0xaaaaaa, wireframe: true })
    : (renderMode === 'flat' && color !== undefined) ? new THREE.MeshLambertMaterial({ color, side: THREE.DoubleSide })
    : new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.5, side: THREE.DoubleSide, color: tex ? 0xffffff : color });
  return new THREE.Mesh(g, mat);
}
function part(px, py, pz) { const o = new THREE.Object3D(); o.position.set(px, py, pz); o.rotation.order = 'ZYX'; return o; }

// ------------------------------------------------------------------ worn-piece placement (cape, armor)
// assets/shrinecraft/worn_display.json: per worn piece a pivot, translation, rotation and scale in PLAYER MODEL space (x = player's left, y = down, z = back).
// The game applies the same transform around the piece (WornDisplay mixins). W = T(pivot + t) * rotXYZ(r) * S(s) * T(-pivot).
const WORN = (D.worn && D.worn.pieces) || {};
const origWorn = {}; Object.keys(WORN).forEach((k) => { origWorn[k] = { translation: WORN[k].translation, rotation: WORN[k].rotation, scale: WORN[k].scale, groups: JSON.parse(JSON.stringify(WORN[k].groups || {})) }; });
const wornEdits = {};
const WORN_KEY = 'tuff-worn-edits-v1';
try { Object.assign(wornEdits, JSON.parse(localStorage.getItem(WORN_KEY) || localStorage.getItem('swag-worn-edits-v1') || '{}')); } catch (e) { /* ignore */ }   // (old key kept as a fallback so earlier edits are not lost)
function wornBlock(piece) { return wornEdits[piece] || origWorn[piece] || { translation: [0, 0, 0], rotation: [0, 0, 0], scale: [1, 1, 1] }; }
// Per-part edits of an armor piece (horns, crown, shoulders ...): translation (model pixels, y down / z back) and scale, about the part's own pivot.
const GROUP_LABEL = { helm: 'Helmet shell', glow: 'Gem + glow lines', crown: 'Crystal crown', neck: 'Neck guard', chest: 'Chest plates', core: 'Chest crystal + veins', back: 'Back plate', shoulders: 'Shoulders', arms: 'Arms', belt: 'Belt', thighs: 'Thigh plates', knees: 'Knee guards', greaves: 'Greaves', soles: 'Soles', trim: 'Glow trim' };
const FINE = (D.extras && D.extras.fine_scale) || 1;
function groupsOf(piece) {
  const seen = [];
  ((D.extras && D.extras.pieces && D.extras.pieces[piece]) || []).forEach((pc) => { if (pc.group && pc.group !== 'cracks' && !seen.includes(pc.group)) seen.push(pc.group); });
  return seen;
}
function groupBlock(piece, g) { const gs = wornBlock(piece).groups || {}; return gs[g] || { translation: [0, 0, 0], scale: [1, 1, 1] }; }
function placeGroupHolder(h) {      // same maths as ArmorExtrasModel.setAngles: T(pivot + t) * S(s), in the 0.5-scaled "fine" space
  const u = h.userData, e = groupBlock(u.piece, u.group), px = u.pivot;
  const sx = u.part === 'right_arm' ? -1 : 1;         // positive X = outward on both arms
  h.position.set((px[0] + e.translation[0] * sx) / FINE, (px[1] + e.translation[1]) / FINE, (px[2] + e.translation[2]) / FINE);
  h.scale.set(e.scale[0], e.scale[1], e.scale[2]);
}
function wornMatrix(piece) {
  const b = wornBlock(piece), p = (WORN[piece] && WORN[piece].pivot) || [0, 0, 0];
  return mul(T(p[0] + b.translation[0], p[1] + b.translation[1], p[2] + b.translation[2]), rotXYZ(b.rotation), S(b.scale[0], b.scale[1], b.scale[2]), T(-p[0], -p[1], -p[2]));
}

// ------------------------------------------------------------------ mannequin (Steve) + armor + cape
const wornState = { shrine_helmet: false, shrine_chestplate: false, shrine_leggings: false, shrine_boots: false, strange_cape: false };
let showExtras = true;
const ARMOR_SLOT = { shrine_helmet: 'head', shrine_chestplate: 'chest', shrine_leggings: 'legs', shrine_boots: 'feet' };

function buildMannequin(opts) {
  const skin = getTexture(D.skin);
  const root = new THREE.Group();
  root.matrixAutoUpdate = false;
  root.matrix.copy(E2W);
  const sk = (b, extra) => boxMesh(Object.assign({ extra: 0 }, b, extra ? { extra } : {}), skin, 64, 64, 0xcc9977);
  const parts = {};
  parts.head = part(0, 0, 0); parts.body = part(0, 0, 0);
  parts.right_arm = part(-5, 2, 0); parts.left_arm = part(5, 2, 0);
  parts.right_leg = part(-1.9, 12, 0); parts.left_leg = part(1.9, 12, 0);
  Object.values(parts).forEach((p) => root.add(p));
  parts.head.add(sk({ pos: [-4, -8, -4], size: [8, 8, 8], uv: [0, 0] }), sk({ pos: [-4, -8, -4], size: [8, 8, 8], uv: [32, 0] }, 0.5));
  parts.body.add(sk({ pos: [-4, 0, -2], size: [8, 12, 4], uv: [16, 16] }), sk({ pos: [-4, 0, -2], size: [8, 12, 4], uv: [16, 32] }, 0.25));
  parts.right_arm.add(sk({ pos: [-3, -2, -2], size: [4, 12, 4], uv: [40, 16] }), sk({ pos: [-3, -2, -2], size: [4, 12, 4], uv: [40, 32] }, 0.25));
  parts.left_arm.add(sk({ pos: [-1, -2, -2], size: [4, 12, 4], uv: [32, 48] }), sk({ pos: [-1, -2, -2], size: [4, 12, 4], uv: [48, 48] }, 0.25));
  parts.right_leg.add(sk({ pos: [-2, 0, -2], size: [4, 12, 4], uv: [0, 16] }), sk({ pos: [-2, 0, -2], size: [4, 12, 4], uv: [0, 32] }, 0.25));
  parts.left_leg.add(sk({ pos: [-2, 0, -2], size: [4, 12, 4], uv: [16, 48] }), sk({ pos: [-2, 0, -2], size: [4, 12, 4], uv: [0, 48] }, 0.25));

  const wornRoots = {};
  const wornPart = (piece, pname) => {
    if (!wornRoots[piece]) { const g = new THREE.Group(); g.matrixAutoUpdate = false; g.matrix.copy(wornMatrix(piece)); root.add(g); wornRoots[piece] = { parts: {} }; wornRoots[piece].g = g; }
    const w = wornRoots[piece];
    if (!w.parts[pname]) { const sp = part(0, 0, 0); w.g.add(sp); w.parts[pname] = sp; }
    return w.parts[pname];
  };
  const A = (name) => D.armor[name];
  const arm = (name, layer) => getTexture(A(name)[layer]);
  const add = (p, b, tex) => p.add(boxMesh(b, tex, 64, 32, 0x888899));
  if (wornState.shrine_helmet) {
    const t = arm('shrine_helmet', 'humanoid');
    add(wornPart('shrine_helmet', 'head'), { pos: [-4, -8, -4], size: [8, 8, 8], uv: [0, 0], extra: 1.0 }, t);
    add(wornPart('shrine_helmet', 'head'), { pos: [-4, -8, -4], size: [8, 8, 8], uv: [32, 0], extra: 1.5 }, t);
  }
  if (wornState.shrine_chestplate) {
    const t = arm('shrine_chestplate', 'humanoid');
    add(wornPart('shrine_chestplate', 'body'), { pos: [-4, 0, -2], size: [8, 12, 4], uv: [16, 16], extra: 1.0 }, t);
    add(wornPart('shrine_chestplate', 'right_arm'), { pos: [-3, -2, -2], size: [4, 12, 4], uv: [40, 16], extra: 1.0 }, t);
    add(wornPart('shrine_chestplate', 'left_arm'), { pos: [-1, -2, -2], size: [4, 12, 4], uv: [40, 16], extra: 1.0, mirror: true }, t);
  }
  if (wornState.shrine_leggings) {
    const t = arm('shrine_leggings', 'leggings');
    add(wornPart('shrine_leggings', 'body'), { pos: [-4, 0, -2], size: [8, 12, 4], uv: [16, 16], extra: 0.5 }, t);
    add(wornPart('shrine_leggings', 'right_leg'), { pos: [-2, 0, -2], size: [4, 12, 4], uv: [0, 16], extra: 0.4 }, t);
    add(wornPart('shrine_leggings', 'left_leg'), { pos: [-2, 0, -2], size: [4, 12, 4], uv: [0, 16], extra: 0.4, mirror: true }, t);
  }
  if (wornState.shrine_boots) {
    const t = arm('shrine_boots', 'humanoid');
    add(wornPart('shrine_boots', 'right_leg'), { pos: [-2, 0, -2], size: [4, 12, 4], uv: [0, 16], extra: 0.9 }, t);
    add(wornPart('shrine_boots', 'left_leg'), { pos: [-2, 0, -2], size: [4, 12, 4], uv: [0, 16], extra: 0.9, mirror: true }, t);
  }
  const live = { roots: wornRoots, holders: [] };
  if (showExtras && D.extras) {
    const et = getTexture(D.extras.texture_url);
    const [etw, eth] = D.extras.texture_size;
    const fineNodes = {}, groupNodes = {};
    for (const name of Object.keys(D.extras.pieces)) {
      if (!wornState[name]) continue;
      for (const pc of D.extras.pieces[name]) {
        let parent = wornPart(name, pc.part), pv = [0, 0, 0];
        if (D.extras.fine_scale) {
          const fk = name + '|' + pc.part;
          if (!fineNodes[fk]) { const f = new THREE.Object3D(); f.scale.set(FINE, FINE, FINE); parent.add(f); fineNodes[fk] = f; }
          const grp = pc.group || 'all', gk = fk + '|' + grp;
          pv = ((D.extras.groups || {})[grp] || {}).pivot || [0, 0, 0];
          if (!groupNodes[gk]) {
            const h = new THREE.Object3D();
            h.userData = { piece: name, part: pc.part, group: grp, pivot: pv };
            placeGroupHolder(h); fineNodes[fk].add(h); groupNodes[gk] = h; live.holders.push(h);
          }
          parent = groupNodes[gk];
          pv = [pv[0] / FINE, pv[1] / FINE, pv[2] / FINE];
        }
        const holder = new THREE.Object3D();
        const ro = pc.rotation_origin || [0, 0, 0];
        holder.position.set(ro[0], ro[1], ro[2]);
        holder.rotation.order = 'ZYX';
        const rr = pc.rotation || [0, 0, 0];
        holder.rotation.set(rr[0] * DEG, rr[1] * DEG, rr[2] * DEG);
        const o = pc.origin;
        const mesh = boxMesh({ pos: [o[0] - ro[0] - pv[0], o[1] - ro[1] - pv[1], o[2] - ro[2] - pv[2]], size: pc.size, uv: pc.uv, extra: pc.inflate || 0, mirror: !!pc.mirror }, et, etw, eth, 0xaa88aa);
        holder.add(mesh);
        if (D.extras.glow_texture_url) {          // the glow pass (game: RenderLayers.eyes): only the glowing texels, bright and additive
          const gm = boxMesh({ pos: [o[0] - ro[0] - pv[0], o[1] - ro[1] - pv[1], o[2] - ro[2] - pv[2]], size: pc.size, uv: pc.uv, extra: (pc.inflate || 0) + 0.02, mirror: !!pc.mirror }, getTexture(D.extras.glow_texture_url), etw, eth, 0xcc88ff);
          gm.material = new THREE.MeshBasicMaterial({ map: getTexture(D.extras.glow_texture_url), transparent: true, alphaTest: 0.05, depthWrite: false, side: THREE.DoubleSide });
          holder.add(gm);
        }
        parent.add(holder);
      }
    }
  }
  if (wornState.strange_cape) {
    const t = getTexture(D.wings);
    const wingL = part(5, 0, 0), wingR = part(-5, 0, 0);
    wingL.rotation.set(15 * DEG, 0, -15 * DEG); wingR.rotation.set(15 * DEG, 0, 15 * DEG);
    wingL.add(boxMesh({ pos: [-10, 0, 0], size: [10, 20, 2], uv: [22, 0], extra: 1.0 }, t, 64, 32, 0x663399));
    wingR.add(boxMesh({ pos: [0, 0, 0], size: [10, 20, 2], uv: [22, 0], extra: 1.0, mirror: true }, t, 64, 32, 0x663399));
    const wings = new THREE.Group();   // ElytraFeatureRenderer: translate(0, 0, 0.125 blocks), after the worn transform
    wings.matrixAutoUpdate = false; wings.matrix.copy(mul(wornMatrix('strange_cape'), T(0, 0, 2)));
    wings.add(wingL, wingR);
    root.add(wings);
    live.wings = wings;
  }
  if (opts && opts.holding) parts.right_arm.rotation.x = ARM_PITCH * DEG;
  Object.keys(wornRoots).forEach((pc) => Object.keys(wornRoots[pc].parts).forEach((pn) => { const sp = wornRoots[pc].parts[pn], rp = parts[pn]; sp.position.copy(rp.position); sp.rotation.copy(rp.rotation); }));
  root.userData.parts = parts;
  root.userData.live = live;
  return root;
}

// ------------------------------------------------------------------ scene + camera
const canvas = $('c');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
renderer.outputEncoding = THREE.sRGBEncoding;
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x20232c);
const persp = new THREE.PerspectiveCamera(40, 1, 0.5, 2000);
const ortho = new THREE.OrthographicCamera(-20, 20, 20, -20, -500, 500);
let camera = persp;
const controls = new THREE.OrbitControls(persp, canvas);
controls.enableDamping = false;
controls.addEventListener('change', requestRender);
const lights = new THREE.Group();
scene.add(lights);
const ambient = new THREE.AmbientLight(0xffffff, 0.55);
const sun = new THREE.DirectionalLight(0xffffff, 0.5); sun.position.set(-30, 60, 40);
const fill = new THREE.DirectionalLight(0xbbccff, 0.15); fill.position.set(40, 10, -30);
lights.add(ambient, sun, fill);
const dyn = new THREE.Group();
scene.add(dyn);
const decor = new THREE.Group();   // grid, reference
scene.add(decor);

let state = { target: 'item', group: null, show: 'ours', item: 'cloud_lance', model: 'cloud_lance', ctx: 'first', grid: true, ref: true, compare: false, compareSet: new Set(), spin: true };
let needsRender = true;
function requestRender() { needsRender = true; }

function clearGroup(g) {
  while (g.children.length) {
    const c = g.children.pop();
    c.traverse((o) => { if (o.geometry) o.geometry.dispose(); if (o.material) { (Array.isArray(o.material) ? o.material : [o.material]).forEach((m) => m.dispose()); } });
  }
}
function lineBox(w, h, d, color) {
  const g = new THREE.EdgesGeometry(new THREE.BoxGeometry(w, h, d));
  const l = new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color }));
  return l;
}
function buildDecor(opts) {
  clearGroup(decor);
  if (state.grid) {
    const n = opts.gridCells || 6;
    const grid = new THREE.GridHelper(16 * n, n, 0x6a7088, 0x3a3f50);
    grid.position.set(opts.gridCenter ? opts.gridCenter[0] : 0, 0, opts.gridCenter ? opts.gridCenter[1] : 0);
    decor.add(grid);
  }
  if (state.ref && opts.blockRef) {
    const b = lineBox(16, 16, 16, 0x5ec9ff);
    b.position.set(opts.blockRef[0], 8, opts.blockRef[1]);
    decor.add(b);
    // 32-unit player height line
    const pts = [new THREE.Vector3(opts.blockRef[0] - 12, 32, opts.blockRef[1]), new THREE.Vector3(opts.blockRef[0] + 12, 32, opts.blockRef[1])];
    const hl = new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), new THREE.LineBasicMaterial({ color: 0xffb347 }));
    decor.add(hl);
  }
}

function setCamera(pos, target, fov) {
  camera = persp;
  controls.object = persp;
  persp.fov = fov || 40;
  persp.position.set(pos[0], pos[1], pos[2]);
  controls.target.set(target[0], target[1], target[2]);
  controls.enabled = true;
  persp.updateProjectionMatrix();
  controls.update();
}
function itemDisplay(modelName, key) {
  const d = edits[modelName] || D.models[modelName].json.display || {};
  return d[key] || { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
}
// ---- display EDITS (owner's slider changes). edits[model] = full display block; orig[model] = what the mod has now.
const edits = {};
const orig = {};
Object.keys(D.models).forEach((n) => { orig[n] = JSON.parse(JSON.stringify(D.models[n].json.display || {})); });
const EDIT_KEY = 'tuff-display-edits-v1';
try { const saved = JSON.parse(localStorage.getItem(EDIT_KEY) || '{}'); Object.keys(saved).forEach((n) => { if (D.models[n] && !D.models[n].vanilla) edits[n] = saved[n]; }); } catch (e) { /* storage may be blocked */ }
function saveEdits() { try { localStorage.setItem(EDIT_KEY, JSON.stringify(edits)); } catch (e) { /* ignore */ } }
const VANILLA_FOR = { sword: 'netherite_sword', axe: 'netherite_axe', pick: 'netherite_pickaxe', spear: 'netherite_spear', mace: 'mace', bow: 'bow', crossbow: 'crossbow', trident: 'trident' };
function refFor(modelName) {
  const m = D.meta[modelKey(modelName)];
  const r = m && VANILLA_FOR[m.kind];
  return r && D.models['vanilla_' + r] ? r : null;
}
function placeModel(group, matrix) {
  group.matrixAutoUpdate = false;
  group.matrix.copy(matrix);
  group.matrixWorldNeedsUpdate = true;
}

function rebuild() {
  clearGroup(dyn);
  pickables.length = 0;
  const ctx = state.compare ? 'compare' : state.ctx;
  ortho.zoom = 1;
  const ref = refFor(state.model);
  const vanillaOnly = state.show === 'vanilla' && ref && ctx !== 'model';
  const modelName = vanillaOnly ? 'vanilla_' + ref : state.model;
  const isBlock = D.models[modelName].block;
  document.body.dataset.ctx = ctx;
  renderer.setClearColor(0x20232c);
  scene.background = new THREE.Color(0x20232c);

  if (ctx === 'model') {
    const g = buildModelGroup(modelName, { pickable: true });
    dyn.add(g);
    const b = modelBounds(D.models[modelName].json);
    const c = b.getCenter(new THREE.Vector3()), sz = b.getSize(new THREE.Vector3());
    buildDecor({ blockRef: [8, 8], gridCenter: [8, 8], gridCells: 4 });
    const dist = Math.max(sz.x, sz.y, sz.z, 16) * 1.25 + 8;
    if (!state.keepCamera) setCamera([c.x + dist * 0.55, c.y + dist * 0.35, c.z + dist * 0.85], [c.x, c.y, c.z]);
  } else if (ctx === 'gui') {
    buildDecor({}); clearGroup(decor);
    const slot = new THREE.Mesh(new THREE.PlaneGeometry(16, 16), new THREE.MeshBasicMaterial({ color: 0x8b8b8b }));
    slot.position.z = -50; decor.add(slot);
    const frame = lineBox(16, 16, 0.1, 0x373737); frame.position.z = -49; decor.add(frame);
    const g = buildModelGroup(modelName, { pickable: true });
    placeModel(g, displayMatrix(itemDisplay(modelName, 'gui')));
    dyn.add(g);
    camera = ortho; controls.enabled = false;
    ortho.position.set(0, 0, 100); ortho.lookAt(0, 0, 0);
    sizeOrtho(11);
    setGuiLights();
  } else if (ctx === 'ground') {
    buildDecor({ gridCells: 2 });
    const g = buildModelGroup(modelName, { pickable: false });
    const spinner = new THREE.Group();
    const inner = new THREE.Group();
    placeModel(g, mul(T(0, 4, 0), displayMatrix(itemDisplay(modelName, 'ground'))));
    spinner.add(g);
    spinner.userData.spin = true;
    dyn.add(spinner);
    if (!state.keepCamera) setCamera([22, 16, 30], [0, 6, 0]);
    setWorldLights();
  } else if (ctx === 'fixed') {
    buildDecor({}); clearGroup(decor);
    const wall = new THREE.Mesh(new THREE.PlaneGeometry(60, 60), new THREE.MeshLambertMaterial({ color: 0x6b655c }));
    wall.position.z = -1.5; decor.add(wall);
    const framePlate = new THREE.Mesh(new THREE.BoxGeometry(12, 12, 1), new THREE.MeshLambertMaterial({ color: 0x8a6a3a }));
    framePlate.position.z = -0.6; decor.add(framePlate);
    const g = buildModelGroup(modelName, { pickable: false });
    // item frame facing south: viewer stands south (+z) looking north
    placeModel(g, mul(T(0, 0, 0.9), RY(180), S(0.5), displayMatrix(itemDisplay(modelName, 'fixed'))));
    dyn.add(g);
    if (!state.keepCamera) setCamera([0, 0, 34], [0, 0, 0], 40);
    controls.enabled = true;
    setWorldLights();
  } else if (ctx === 'first') {
    buildDecor({}); clearGroup(decor);
    const g = buildModelGroup(modelName, { pickable: false });
    placeModel(g, mul(T(0.56 * 16, -0.52 * 16, -0.72 * 16), displayMatrix(itemDisplay(modelName, 'firstperson_righthand'))));
    dyn.add(g);
    if (state.show === 'both' && ref) {          // the vanilla item in the LEFT hand, mirrored, for side-by-side comparison
      const gv = buildModelGroup('vanilla_' + ref, { pickable: false });
      placeModel(gv, mul(T(-0.56 * 16, -0.52 * 16, -0.72 * 16), displayMatrix(itemDisplay('vanilla_' + ref, 'firstperson_righthand'), true)));
      dyn.add(gv);
    }
    camera = persp; controls.enabled = false;
    persp.fov = 70; persp.position.set(0, 0, 0); persp.up.set(0, 1, 0); persp.lookAt(0, 0, -10); persp.updateProjectionMatrix();
    scene.background = new THREE.Color(0x6f98c8);
    // a tiny horizon so you can judge the angle
    const ground = new THREE.Mesh(new THREE.PlaneGeometry(400, 400), new THREE.MeshLambertMaterial({ color: 0x5a7a4a }));
    ground.rotation.x = -Math.PI / 2; ground.position.y = -28; decor.add(ground);
    setWorldLights();
  } else if (ctx === 'third') {
    buildDecor({ gridCells: 3 });
    const man = buildMannequin({ holding: true });
    dyn.add(man);
    const g = buildModelGroup(modelName, { pickable: false });
    placeModel(g, mul(E2W, thirdChain(), displayMatrix(itemDisplay(modelName, 'thirdperson_righthand'))));
    dyn.add(g);
    if (state.show === 'both' && ref) {
      const OFF = T(26, 0, 0);
      const man2 = buildMannequin({ holding: true });
      man2.matrix.premultiply(OFF);
      dyn.add(man2);
      const gv = buildModelGroup('vanilla_' + ref, { pickable: false });
      placeModel(gv, mul(OFF, E2W, thirdChain(), displayMatrix(itemDisplay('vanilla_' + ref, 'thirdperson_righthand'))));
      dyn.add(gv);
      if (!state.keepCamera) setCamera([-50, 20, 40], [10, 14, 6]);
    } else if (!state.keepCamera) setCamera([-50, 20, 26], [-3, 14, 6]);
    setWorldLights();
  } else if (ctx === 'compare') {
    buildCompare();
  }
  if (ctx === 'model') setWorldLights();
  updateInspectorList();
  requestRender();
}
function sizeOrtho(half) {
  const w = canvas.clientWidth, h = canvas.clientHeight;
  const a = w / h;
  ortho.left = -half * a; ortho.right = half * a; ortho.top = half; ortho.bottom = -half;
  ortho.updateProjectionMatrix();
}
function setGuiLights() {
  // Minecraft "side" item lighting: bright from the top, a little from the left/front
  ambient.intensity = 0.55; sun.position.set(-20, 60, 50); sun.intensity = 0.5; fill.position.set(30, 10, 30); fill.intensity = 0.15;
}
function setWorldLights() {
  ambient.intensity = 0.55; sun.position.set(-30, 60, 40); sun.intensity = 0.5; fill.position.set(40, 10, -30); fill.intensity = 0.15;
}

// ------------------------------------------------------------------ comparison mode
function buildCompare() {
  buildDecor({ gridCells: 8, gridCenter: [60, 0] });
  const man = buildMannequin({ holding: false });
  dyn.add(man);
  let x = 26;
  const names = D.items.filter((it) => state.compareSet.has(it.id));
  names.forEach((it) => {
    const mname = it.models[0].name;
    const model = D.models[mname].json;
    const g = buildModelGroup(mname, { pickable: false });
    const b = modelBounds(model);
    const sz = b.getSize(new THREE.Vector3());
    const disp = model.display && model.display.thirdperson_righthand;
    const k = (disp ? disp.scale[0] : 1) * 0.9375;       // real size next to the player when held
    const cx = (b.min.x + b.max.x) / 2, cz = (b.min.z + b.max.z) / 2;
    g.matrixAutoUpdate = false;
    g.matrix.copy(mul(T(x, 0, 0), S(k), T(-cx, -b.min.y, -cz)));
    dyn.add(g);
    // label as a small sprite
    const spr = makeLabel(it.label);
    spr.position.set(x, -4, 0);
    dyn.add(spr);
    x += Math.max(sz.x, sz.z, 6) * k + 9;
  });
  setCamera([x / 2 + 6, 26, 90 + x * 0.35], [x / 2, 16, 0], 40);
  setWorldLights();
}
function makeLabel(text) {
  const c = document.createElement('canvas'); c.width = 256; c.height = 48;
  const g = c.getContext('2d'); g.font = 'bold 26px sans-serif'; g.fillStyle = '#e8ecf6'; g.textAlign = 'center'; g.fillText(text, 128, 32);
  const t = new THREE.CanvasTexture(c);
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, depthTest: false }));
  s.scale.set(24, 4.5, 1);
  return s;
}

// ------------------------------------------------------------------ inspector
function fmt(a) { return a.map((v) => (Math.round(v * 100) / 100)).join(', '); }
function updateInspectorList() {
  const list = $('elements'); list.innerHTML = '';
  const model = D.models[state.model].json;
  model.elements.forEach((el) => {
    const d = document.createElement('div');
    d.className = 'el' + (selectedElement && selectedElement.name === el.name && selectedElement.model === state.model ? ' sel' : '');
    const sw = document.createElement('span'); sw.className = 'sw'; sw.style.background = '#' + hashColor(el.name).getHexString();
    d.appendChild(sw); d.appendChild(document.createTextNode(el.name));
    d.onclick = () => selectElement(state.model, el.name);
    list.appendChild(d);
  });
  showSelection();
}
function showSelection() {
  const box = $('selinfo');
  if (!selectedElement || selectedElement.model !== state.model) { box.innerHTML = '<i>Click an element in the 3D view (model context) or in the list above.</i>'; return; }
  const el = D.models[state.model].json.elements.find((e) => e.name === selectedElement.name);
  if (!el) { box.innerHTML = ''; return; }
  const size = [el.to[0] - el.from[0], el.to[1] - el.from[1], el.to[2] - el.from[2]];
  const meta = (D.meta[modelKey(state.model)] || {}).elements || {};
  let rot = 'none';
  if (el.rotation) rot = `${el.rotation.axis || 'euler'} ${el.rotation.angle !== undefined ? el.rotation.angle : ''}&deg; about [${fmt(el.rotation.origin)}]`;
  box.innerHTML = `<b>${el.name}</b><br>from [${fmt(el.from)}]<br>to [${fmt(el.to)}]<br>size [${fmt(size)}] units<br>rotation: ${rot}<br>paint material: ${meta[el.name] || '(by name prefix)'}<br>faces: ${Object.keys(el.faces).join(', ')}`;
}
function modelKey(n) { return n.replace('block/', ''); }
function selectElement(model, name) { selectedElement = { model, name }; rebuild(); }

canvas.addEventListener('click', (ev) => {
  if (state.ctx !== 'model' || state.compare) return;
  const r = canvas.getBoundingClientRect();
  const ndc = new THREE.Vector2(((ev.clientX - r.left) / r.width) * 2 - 1, -((ev.clientY - r.top) / r.height) * 2 + 1);
  const rc = new THREE.Raycaster(); rc.setFromCamera(ndc, camera);
  const hit = rc.intersectObjects(pickables, false)[0];
  if (hit) { state.keepCamera = true; selectElement(hit.object.userData.model, hit.object.userData.element.name); state.keepCamera = false; }
});


// ------------------------------------------------------------------ position editor (sliders, live)
const CTX_KEY = { gui: 'gui', ground: 'ground', fixed: 'fixed', first: 'firstperson_righthand', third: 'thirdperson_righthand' };
const CTX_NAME = { gui: 'Inventory', ground: 'On the ground', fixed: 'Item frame', first: 'First person', third: 'Third person, right hand' };
const ROWS = [
  ['translation', 0, 'Move X (right / left)', -40, 40, 0.1], ['translation', 1, 'Move Y (up / down)', -40, 40, 0.1], ['translation', 2, 'Move Z (toward you / away)', -40, 40, 0.1],
  ['scale', 0, 'Scale X', 0.05, 3, 0.01], ['scale', 1, 'Scale Y', 0.05, 3, 0.01], ['scale', 2, 'Scale Z', 0.05, 3, 0.01],
  ['rotation', 0, 'Raw rotation X', -180, 180, 1], ['rotation', 1, 'Raw rotation Y', -180, 180, 1], ['rotation', 2, 'Raw rotation Z', -180, 180, 1],
];
// Rotation sliders that always act around DIFFERENT axes. Minecraft stores rotation as Euler X, Y, Z, and when Y is near 90 degrees X and Z
// turn the same way ("gimbal lock"). These sliders instead turn the item around the fixed axes of the view (left-right, up-down, toward-you),
// on top of its current rotation, and the Euler numbers are recalculated from the result.
const VIEW_ROWS = [
  ['Tilt: tip up / down (around the left-right axis)', 0], ['Turn: swing left / right (around the up-down axis)', 1], ['Roll: spin (around the toward-you axis)', 2],
];
function eulerFromMatrix(m) {          // m = Rx * Ry * Rz (Minecraft order); three.js stores column-major
  const e = m.elements, M = (r, c) => e[c * 4 + r];
  const sb = Math.max(-1, Math.min(1, M(0, 2)));
  const b = Math.asin(sb);
  let a, c;
  if (Math.abs(sb) < 0.99999) { a = Math.atan2(-M(1, 2), M(2, 2)); c = Math.atan2(-M(0, 1), M(0, 0)); }
  else { a = Math.atan2(M(1, 0), M(1, 1)); c = 0; }
  return [a / DEG, b / DEG, c / DEG].map((v) => Math.round(v * 100) / 100 + 0);
}
const viewDrag = {};            // per model: the rotation matrix when the current drag started
let viewSliders = [];
const clone = (o) => JSON.parse(JSON.stringify(o));
const r2 = (v) => Math.round(v * 100) / 100;
let edRows = [];
function itemModels() { const it = D.items.find((i) => i.id === state.item); return it ? it.models.map((m) => m.name) : [state.model]; }
function edTargets() { return $('edAll').checked ? itemModels() : [state.model]; }
function ensureEdit(name) { if (!edits[name]) edits[name] = clone(orig[name]); return edits[name]; }
function blockOf(name, key) {
  const d = ensureEdit(name);
  if (!d[key]) d[key] = { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
  return d[key];
}
function edKey() { return state.compare ? null : CTX_KEY[state.ctx] || null; }
// What the sliders edit: the held item (default) or a WORN piece (cape / armor) when it is on the mannequin, selected, and the Third person view is showing.
function wornTarget() { return (state.target && state.target !== 'item' && wornState[state.target] && state.ctx === 'third' && !state.compare) ? state.target : null; }
const WORN_LABEL = { shrine_helmet: 'Shrine Helmet', shrine_chestplate: 'Shrine Chestplate', shrine_leggings: 'Shrine Leggings', shrine_boots: 'Shrine Boots', strange_cape: '"Strange" Cape' };
const wsign = (field, i) => (field === 'translation' && i > 0) ? -1 : 1;     // worn translation is stored in model space (y down, z back); the sliders show up / forward as +
function ensureWorn(piece) {
  if (!wornEdits[piece]) wornEdits[piece] = clone(origWorn[piece] || { translation: [0, 0, 0], rotation: [0, 0, 0], scale: [1, 1, 1] });
  if (!wornEdits[piece].groups) wornEdits[piece].groups = {};
  return wornEdits[piece];
}
function ensureGroup(piece, g) { const b = ensureWorn(piece); if (!b.groups[g]) b.groups[g] = clone(groupBlock(piece, g)); return b.groups[g]; }
// the selected part of the worn piece the sliders edit (null = the whole piece)
function curGroup() { const wt = wornTarget(); return (wt && state.group && groupsOf(wt).includes(state.group)) ? state.group : null; }
// Move the armor on the mannequin(s) without rebuilding the ~2000 little blocks (keeps sliders smooth). Returns false when a rebuild is needed.
function liveWorn() {
  const wt = wornTarget(); if (!wt || wt === 'strange_cape') return false;
  const sel = curGroup();
  const mans = dyn.children.filter((c) => c.userData && c.userData.live);
  if (!mans.length) return false;
  mans.forEach((m) => {
    const lv = m.userData.live;
    Object.keys(lv.roots).forEach((pc) => lv.roots[pc].g.matrix.copy(wornMatrix(pc)));
    lv.holders.forEach((h) => {
      placeGroupHolder(h);
      const on = sel && h.userData.piece === wt && h.userData.group === sel;      // the part being edited glows a little
      h.traverse((o) => { if (o.material && o.material.emissive) o.material.emissive.setHex(on ? 0x663311 : 0x000000); });
    });
  });
  requestRender();
  return true;
}
function saveWorn() { try { localStorage.setItem(WORN_KEY, JSON.stringify(wornEdits)); } catch (e) { /* ignore */ } }
let rebuildPending = false;
function scheduleRebuild() {
  if (rebuildPending) return;
  rebuildPending = true;
  requestAnimationFrame(() => { rebuildPending = false; state.keepCamera = true; rebuild(); state.keepCamera = false; });
}
function buildEditorUI() {
  const body = $('edbody'); body.innerHTML = ''; edRows = []; viewSliders = [];
  VIEW_ROWS.forEach((vr) => {
    const row = document.createElement('div'); row.className = 'edrow';
    const lab = document.createElement('label'); lab.textContent = vr[0];
    const range = document.createElement('input'); range.type = 'range'; range.min = -90; range.max = 90; range.step = 1; range.value = 0; range.id = 'ed_v_' + vr[1];
    const num = document.createElement('input'); num.type = 'number'; num.step = 1; num.value = 0; num.id = 'ed_vn_' + vr[1];
    lab.htmlFor = range.id;
    const drive = (v) => { if (isFinite(v)) { range.value = v; num.value = v; edViewRot(vr[1], v); } };
    range.addEventListener('input', () => drive(parseFloat(range.value)));
    num.addEventListener('input', () => drive(parseFloat(num.value)));
    const end = () => { Object.keys(viewDrag).forEach((k) => delete viewDrag[k]); range.value = 0; num.value = 0; };
    range.addEventListener('change', end); num.addEventListener('change', end);
    row.appendChild(lab); row.appendChild(range); row.appendChild(num);
    body.appendChild(row); viewSliders.push({ range, num });
  });
  const hdr = document.createElement('div'); hdr.style.cssText = 'color:var(--dim);font-size:11px;margin:6px 0 6px'; hdr.textContent = 'Position and size (the rotation sliders above add on top of the current rotation, so each one always turns a different way):';
  body.appendChild(hdr);
  ROWS.forEach((r) => {
    const row = document.createElement('div'); row.className = 'edrow';
    const lab = document.createElement('label'); lab.textContent = r[2];
    const range = document.createElement('input'); range.type = 'range'; range.min = r[3]; range.max = r[4]; range.step = r[5];
    const num = document.createElement('input'); num.type = 'number'; num.min = r[3] * 2; num.max = r[4] * 2; num.step = r[5];
    range.id = 'ed_r_' + r[0] + r[1]; num.id = 'ed_n_' + r[0] + r[1];
    lab.htmlFor = range.id;
    const on = (src) => () => { let v = parseFloat(src.value); if (!isFinite(v)) return; range.value = v; num.value = v; edSet(r[0], r[1], v); };
    range.addEventListener('input', on(range)); num.addEventListener('change', on(num)); num.addEventListener('input', on(num));
    row.appendChild(lab); row.appendChild(range); row.appendChild(num);
    body.appendChild(row);
    edRows.push({ field: r[0], i: r[1], range, num, row });
  });
  $('edAll').onchange = () => {};
  $('edReset').onclick = () => {
    const wt = wornTarget();
    if (wt) { const g = curGroup(); if (g) { if (wornEdits[wt] && wornEdits[wt].groups) delete wornEdits[wt].groups[g]; } else delete wornEdits[wt]; afterEdit(true); return; }
    const key = edKey(); if (!key) return;
    edTargets().forEach((n) => { if (orig[n][key]) { ensureEdit(n)[key] = clone(orig[n][key]); if (key === 'fixed' && orig[n].fixed) edits[n].on_shelf = clone(orig[n].fixed); } });
    afterEdit(true);
  };
  $('edResetItem').onclick = () => { const wt = wornTarget(); if (wt) delete wornEdits[wt]; else itemModels().forEach((n) => { delete edits[n]; }); afterEdit(true); };
  $('edCopy').onclick = () => {
    const ta = $('edout'); ta.focus(); ta.select();
    const done = () => { $('edcopied').textContent = 'Copied. Paste it into the chat.'; };
    const fail = () => { $('edcopied').textContent = 'Press Ctrl+C (or long-press, Copy) on the selected text.'; };
    try { navigator.clipboard.writeText(ta.value).then(done, fail); } catch (e) { fail(); }
  };
  $('edReveal').onclick = () => { $('edout').hidden = !$('edout').hidden; };
  $('edSave').onclick = () => { clearTimeout(saveTimer); setSaveStatus('Saving...', true); writeSaved(); };
  $('edDownload').onclick = async () => {
    try { await dl.save({ filename: 'tuff-display-changes.json', data: $('edout').value }); setSaveStatus('File saved.', true); }
    catch (e) { if (!e || e.code !== 'declined') setSaveStatus('Could not save the file here. Use Copy changes.', false); }
  };
}
function edViewRot(axis, deg) {
  const wt = wornTarget();
  if (wt && curGroup()) return;          // parts have no rotation, only move + size
  if (wt) {
    const AXW = [RX, RY, RZ][axis], dd = axis === 0 ? deg : -deg;        // world-axis turn -> model-space (y and z are flipped)
    const b = ensureWorn(wt);
    if (!viewDrag[wt]) viewDrag[wt] = rotXYZ(b.rotation);
    b.rotation = eulerFromMatrix(mul(AXW(dd), viewDrag[wt]));
    edRows.filter((r) => r.field === 'rotation').forEach((r) => { r.range.value = r2(b.rotation[r.i]); r.num.value = r2(b.rotation[r.i]); });
    afterEdit(false); return;
  }
  const key = edKey(); if (!key) return;
  const AX = [RX, RY, RZ][axis];
  edTargets().forEach((n) => {
    const b = blockOf(n, key);
    if (!viewDrag[n]) viewDrag[n] = rotXYZ(b.rotation);
    b.rotation = eulerFromMatrix(mul(AX(deg), viewDrag[n]));
    if (key === 'fixed') edits[n].on_shelf = clone(edits[n].fixed);
  });
  const cur = (edits[state.model] || orig[state.model])[key].rotation;
  edRows.filter((r) => r.field === 'rotation').forEach((r) => { r.range.value = r2(cur[r.i]); r.num.value = r2(cur[r.i]); });
  afterEdit(false);
}
function edSet(field, i, v) {
  const wt = wornTarget();
  if (wt && curGroup()) {
    if (field === 'rotation') return;
    const b = ensureGroup(wt, curGroup());
    if ($('edLock').checked && field === 'scale') { b.scale = [v, v, v]; edRows.filter((r) => r.field === 'scale').forEach((r) => { r.range.value = v; r.num.value = v; }); }
    else b[field][i] = v * wsign(field, i);
    afterEdit(false); return;
  }
  if (wt) {
    const b = ensureWorn(wt);
    if ($('edLock').checked && field === 'scale') { b.scale = [v, v, v]; edRows.filter((r) => r.field === 'scale').forEach((r) => { r.range.value = v; r.num.value = v; }); }
    else b[field][i] = v * wsign(field, i);
    afterEdit(false); return;
  }
  const key = edKey(); if (!key) return;
  const lockScale = $('edLock').checked && field === 'scale';
  edTargets().forEach((n) => {
    const b = blockOf(n, key);
    if (lockScale) { b.scale = [v, v, v]; } else b[field][i] = v;
    if (key === 'fixed') edits[n].on_shelf = clone(edits[n].fixed);
  });
  if (lockScale) edRows.filter((r) => r.field === 'scale').forEach((r) => { r.range.value = v; r.num.value = v; });
  afterEdit(false);
}
function afterEdit(refreshSliders) {
  saveEdits(); saveWorn(); updateOutput(); queueSave();
  if (refreshSliders) refreshEditor();
  if (!liveWorn()) scheduleRebuild();
}
function normWorn(b) {        // rounded copy; parts left at identity are dropped so "reset" really removes them
  b = b || { translation: [0, 0, 0], rotation: [0, 0, 0], scale: [1, 1, 1] };
  const out = { translation: b.translation.map(r2), rotation: b.rotation.map(r2), scale: b.scale.map(r2) };
  const gs = {};
  Object.keys(b.groups || {}).forEach((g) => {
    const t = b.groups[g].translation.map(r2), s = b.groups[g].scale.map(r2);
    if (t.some((x) => x !== 0) || s.some((x) => x !== 1)) gs[g] = { translation: t, scale: s };
  });
  if (Object.keys(gs).length) out.groups = gs;
  return out;
}
function diffOutput() {
  const models = {}; let n = 0;
  Object.keys(edits).forEach((name) => {
    Object.keys(edits[name]).forEach((key) => {
      if (key === 'on_shelf') return;
      if (JSON.stringify(edits[name][key]) !== JSON.stringify(orig[name][key])) {
        models[name] = models[name] || {}; models[name][key] = { rotation: edits[name][key].rotation.map(r2), translation: edits[name][key].translation.map(r2), scale: edits[name][key].scale.map(r2) };
        n++;
      }
    });
  });
  const worn = {};
  Object.keys(wornEdits).forEach((pc) => {
    const cur = normWorn(wornEdits[pc]);
    if (JSON.stringify(cur) !== JSON.stringify(normWorn(origWorn[pc]))) {
      worn[pc] = cur;
      n += 1 + Object.keys(cur.groups || {}).length;
    }
  });
  return { models, worn, n };
}
function updateOutput() {
  const d = diffOutput();
  const out = { format: 'tuffsmp-display-v1', models: d.models };
  if (Object.keys(d.worn).length) out.worn = d.worn;
  $('edout').value = JSON.stringify(out, null, 1);
  $('edcount').textContent = d.n ? d.n + ' changed setting' + (d.n === 1 ? '' : 's') + ' in ' + (Object.keys(d.models).length + Object.keys(d.worn).length) + ' item(s)' : 'No changes yet';
  $('edcopied').textContent = '';
}
function refreshEditor() {
  // target bar: the held item, plus every worn piece currently on the mannequin
  const bar = $('edtarget'); bar.innerHTML = '';
  const wt = wornTarget();
  const mk = (label, id) => { const bt = document.createElement('button'); bt.textContent = label; bt.className = (id === 'item' ? !wt : wt === id) ? 'on' : ''; bt.onclick = () => { state.target = id; state.group = null; $('edLock').checked = (id === 'item'); if (id !== 'item') state.ctx = 'third'; state.compare = false; markButtons(); rebuild(); }; bar.appendChild(bt); };
  const worn = Object.keys(wornState).filter((n) => wornState[n]);
  bar.hidden = worn.length === 0;
  if (worn.length) { const it = D.items.find((i) => i.id === state.item); mk('Held: ' + (it ? it.label : 'item'), 'item'); worn.forEach((n) => mk('Worn: ' + WORN_LABEL[n], n)); }
  const key = edKey();
  const ok = (!!key && state.show !== 'vanilla') || !!wt;
  $('edctx').textContent = wt ? 'WORN: ' + WORN_LABEL[wt] + (curGroup() ? ' / ' + (GROUP_LABEL[curGroup()] || curGroup()) : '') : (key ? CTX_NAME[state.ctx] : '');
  $('edhelp').hidden = ok;
  $('edhelp').textContent = state.compare ? 'Leave Size comparison to edit.' : (!key ? 'Choose Inventory, Ground, Item frame, First person or Third person above to edit that position.' : 'Switch the item to Ours or Both to edit it (Vanilla is read-only).');
  $('edbody').hidden = !ok; document.querySelector('.edbtns:not(#edtarget)').hidden = !ok;
  if (!ok) return;
  // part bar: the whole piece, or one part of it (horns, crown, shoulders ...)
  const gb = $('edgroup'); gb.innerHTML = '';
  const gl = wt ? groupsOf(wt) : [];
  gb.hidden = gl.length === 0;
  const sg = curGroup();
  if (gl.length) {
    const mkg = (label, id) => { const bt = document.createElement('button'); bt.textContent = label; bt.className = (id === sg) ? 'on' : ''; bt.onclick = () => { state.group = id; refreshEditor(); liveWorn(); }; gb.appendChild(bt); };
    mkg('Whole piece', null); gl.forEach((g) => mkg(GROUP_LABEL[g] || g, g));
  }
  const SIZE_LABEL = ['Width (left-right)', 'Height (up-down)', 'Depth (front-back)'];
  edRows.forEach((r) => {
    const lab = r.row.querySelector('label');
    if (r.field === 'scale') lab.textContent = wt ? SIZE_LABEL[r.i] : ROWS.find((x) => x[0] === 'scale' && x[1] === r.i)[2];
    r.row.hidden = !!(wt && sg && r.field === 'rotation');
  });
  viewSliders.forEach((v) => { v.range.parentNode.hidden = !!(wt && sg); });
  if (wt) {
    const blk = sg ? groupBlock(wt, sg) : wornBlock(wt);
    edRows.forEach((r) => { if (!blk[r.field]) return; const v = r2(blk[r.field][r.i] * wsign(r.field, r.i)); r.range.value = v; r.num.value = v; });
    viewSliders.forEach((v) => { v.range.value = 0; v.num.value = 0; });
    $('edAllRow').hidden = true;
    return;
  }
  const model = D.models[state.model];
  const blk = (edits[state.model] || orig[state.model])[key] || { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
  edRows.forEach((r) => { const v = r2(blk[r.field][r.i]); r.range.value = v; r.num.value = v; });
  viewSliders.forEach((v) => { v.range.value = 0; v.num.value = 0; });
  const same = itemModels().every((n) => JSON.stringify((edits[n] || orig[n])[key]) === JSON.stringify((edits[state.model] || orig[state.model])[key]));
  $('edAll').checked = itemModels().length > 1 && same;
  $('edAllRow').hidden = itemModels().length < 2;
}


// ------------------------------------------------------------------ saving without copy/paste (artifact `db` capability)
// The page keeps the changed positions in one shared document, display/pending. Claude reads it (ArtifactData) and applies it to the mod
// (tools/apply_display.py). Outside the artifact (double-clicked file) this is absent and the Copy button is the way to save.
let saveDoc = null, saveTimer = null, saving = false, saveAgain = false, dl = null;
function setSaveStatus(t, ok) { const e = $('edsave'); if (e) { e.textContent = t; e.style.color = ok === false ? '#ff9d7a' : 'var(--acc)'; } }
async function writeSaved() {
  if (!saveDoc) return;
  if (saving) { saveAgain = true; return; }
  saving = true;
  try {
    const d = diffOutput();
    await saveDoc.set({ format: 'tuffsmp-display-v1', models: d.models, worn: d.worn, savedAt: new Date().toISOString(), count: d.n });
    const t = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    setSaveStatus(d.n ? 'Saved at ' + t + '. Tell Claude "apply my changes".' : 'Saved at ' + t + ' (no changes).', true);
  } catch (e) {
    setSaveStatus('Could not save (' + ((e && e.code) || 'error') + '). Use Copy changes instead.', false);
  }
  saving = false;
  if (saveAgain) { saveAgain = false; writeSaved(); }
}
function queueSave() { if (!saveDoc) return; setSaveStatus('Saving...', true); clearTimeout(saveTimer); saveTimer = setTimeout(writeSaved, 1200); }
async function initSaving() {
  try {
    const c = window.claude;
    if (!c || !c.use) return;
    const db = await c.use('db');
    dl = await c.use('downloads');
    if (dl) $('edDownload').hidden = false;
    if (!db) return;
    saveDoc = db.doc('display/pending');
    $('edSaveRow').hidden = false;
    let snap = null;
    try { snap = await saveDoc.get(); } catch (e) { /* nothing stored yet */ }
    const data = snap && snap.exists ? snap.data() : null;
    if (data && data.worn && Object.keys(data.worn).length) {
      Object.keys(data.worn).forEach((pc) => { if (WORN[pc]) wornEdits[pc] = clone(data.worn[pc]); });
      saveWorn(); updateOutput(); refreshEditor(); scheduleRebuild();
    }
    if (data && ((data.models && Object.keys(data.models).length) || (data.worn && Object.keys(data.worn).length))) {
      Object.keys(data.models || {}).forEach((name) => {
        if (!D.models[name] || D.models[name].vanilla) return;
        Object.keys(data.models[name]).forEach((key) => { ensureEdit(name)[key] = clone(data.models[name][key]); if (key === 'fixed') edits[name].on_shelf = clone(edits[name].fixed); });
      });
      saveEdits(); updateOutput(); refreshEditor(); scheduleRebuild();
      setSaveStatus('Loaded your saved changes (' + (data.count || '') + '). Tell Claude "apply my changes" to put them in the mod.', true);
    } else setSaveStatus('Changes save automatically. Then tell Claude "apply my changes".', true);
  } catch (e) { /* no capabilities here: copy-paste still works */ }
}

// ------------------------------------------------------------------ UI
function buildUI() {
  const il = $('items'); il.innerHTML = '';
  D.items.forEach((it) => {
    const row = document.createElement('div'); row.className = 'item'; row.dataset.id = it.id;
    const cb = document.createElement('input'); cb.type = 'checkbox'; cb.className = 'cmp'; cb.title = 'include in comparison';
    cb.onclick = (e) => { e.stopPropagation(); if (cb.checked) state.compareSet.add(it.id); else state.compareSet.delete(it.id); if (state.compare) rebuild(); };
    row.appendChild(cb);
    row.appendChild(document.createTextNode(it.label));
    row.onclick = () => chooseItem(it.id);
    il.appendChild(row);
  });
  document.querySelectorAll('[data-ctx]').forEach((b) => b.onclick = () => { state.ctx = b.dataset.ctx; state.compare = false; $('cmpToggle').classList.remove('on'); markButtons(); rebuild(); });
  document.querySelectorAll('[data-show]').forEach((b) => b.onclick = () => { state.show = b.dataset.show; markButtons(); rebuild(); });
  buildEditorUI(); updateOutput();
  document.querySelectorAll('[data-render]').forEach((b) => b.onclick = () => { renderMode = b.dataset.render; markButtons(); rebuild(); });
  $('gridToggle').onclick = () => { state.grid = !state.grid; markButtons(); rebuild(); };
  $('refToggle').onclick = () => { state.ref = !state.ref; markButtons(); rebuild(); };
  $('extraToggle').onclick = () => { showExtras = !showExtras; markButtons(); rebuild(); };
  $('cmpToggle').onclick = () => { state.compare = !state.compare; if (state.compare && state.compareSet.size === 0) D.items.forEach((i) => state.compareSet.add(i.id)); syncChecks(); markButtons(); rebuild(); };
  $('wearAll').onclick = () => { ['shrine_helmet', 'shrine_chestplate', 'shrine_leggings', 'shrine_boots'].forEach((n) => wornState[n] = true); wornState.strange_cape = false; if (!['third', 'compare'].includes(state.ctx)) state.ctx = 'third'; markButtons(); rebuild(); };
  $('wearNone').onclick = () => { Object.keys(wornState).forEach((n) => wornState[n] = false); markButtons(); rebuild(); };
  const wearBox = $('wear'); wearBox.innerHTML = '';
  Object.keys(wornState).forEach((n) => {
    const lab = D.items.find((i) => i.id === n).label;
    const b = document.createElement('button'); b.dataset.wear = n; b.textContent = lab;
    b.onclick = () => {
      wornState[n] = !wornState[n];
      if (n === 'strange_cape' && wornState[n]) wornState.shrine_chestplate = false;      // both live in the chest slot
      if (n === 'shrine_chestplate' && wornState[n]) wornState.strange_cape = false;
      if (wornState[n]) { state.target = n; state.compare = false; state.ctx = 'third'; } else if (state.target === n) state.target = 'item';
      markButtons(); rebuild();
    };
    wearBox.appendChild(b);
  });
  markButtons();
}
function syncChecks() { document.querySelectorAll('.item').forEach((r) => { r.querySelector('.cmp').checked = state.compareSet.has(r.dataset.id); }); }
function markButtons() {
  document.querySelectorAll('[data-ctx]').forEach((b) => b.classList.toggle('on', !state.compare && b.dataset.ctx === state.ctx));
  document.querySelectorAll('[data-render]').forEach((b) => b.classList.toggle('on', b.dataset.render === renderMode));
  document.querySelectorAll('[data-wear]').forEach((b) => b.classList.toggle('on', !!wornState[b.dataset.wear]));
  $('gridToggle').classList.toggle('on', state.grid); $('refToggle').classList.toggle('on', state.ref);
  $('extraToggle').classList.toggle('on', showExtras); $('cmpToggle').classList.toggle('on', state.compare);
  document.querySelectorAll('.item').forEach((r) => r.classList.toggle('sel', r.dataset.id === state.item));
  document.querySelectorAll('[data-show]').forEach((b) => { b.classList.toggle('on', b.dataset.show === state.show); b.disabled = b.dataset.show !== 'ours' && !refFor(state.model); });
  refreshEditor();
}
function chooseItem(id) {
  const it = D.items.find((i) => i.id === id);
  state.item = id; state.model = it.models[0].name; selectedElement = null;
  const vs = $('variants'); vs.innerHTML = '';
  if (it.models.length > 1) {
    it.models.forEach((m) => {
      const b = document.createElement('button'); b.textContent = m.label; b.dataset.model = m.name;
      b.onclick = () => { state.model = m.name; selectedElement = null; markVariants(); rebuild(); };
      vs.appendChild(b);
    });
  }
  markVariants(); markButtons(); rebuild();
}
function markVariants() { document.querySelectorAll('#variants button').forEach((b) => b.classList.toggle('on', b.dataset.model === state.model)); }

function renderFrame() {
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (!state.compare && state.ctx === 'first') {
    // Minecraft's window is wide: show the first-person view in a 16:9 frame (vertical FOV 70) so the hand position is realistic.
    let vw = w, vh = Math.round(w * 9 / 16);
    if (vh > h) { vh = h; vw = Math.round(h * 16 / 9); }
    const vx = Math.round((w - vw) / 2), vy = Math.round((h - vh) / 2);
    renderer.setScissorTest(true);
    renderer.setViewport(0, 0, w, h); renderer.setScissor(0, 0, w, h);
    renderer.setClearColor(0x0c0e13); renderer.clear();
    renderer.setViewport(vx, vy, vw, vh); renderer.setScissor(vx, vy, vw, vh);
    persp.aspect = vw / vh; persp.updateProjectionMatrix();
    renderer.render(scene, camera);
    renderer.setScissorTest(false); renderer.setViewport(0, 0, w, h);
    persp.aspect = w / h; persp.updateProjectionMatrix();
  } else {
    renderer.render(scene, camera);
  }
}

// ------------------------------------------------------------------ loop
function resize() {
  const w = canvas.clientWidth, h = canvas.clientHeight;
  renderer.setSize(w, h, false);
  persp.aspect = w / h; persp.updateProjectionMatrix();
  if (camera === ortho) sizeOrtho(11 / ortho.zoom);
  requestRender();
}
window.addEventListener('resize', resize);
canvas.addEventListener('wheel', () => { if (camera === ortho) { setTimeout(() => { ortho.zoom = Math.max(0.3, Math.min(6, ortho.zoom * 1)); ortho.updateProjectionMatrix(); requestRender(); }, 0); } }, { passive: true });
let last = performance.now();
function loop(now) {
  const dt = (now - last) / 1000; last = now;
  dyn.children.forEach((c) => { if (c.userData && c.userData.spin && state.spin) { c.rotation.y += dt * 0.9; needsRender = true; } });
  if (needsRender) { needsRender = false; renderFrame(); }
  requestAnimationFrame(loop);
}

// ------------------------------------------------------------------ boot (+ hooks for the automated screenshot test)
buildUI();
resize();
initSaving();
const params = new URLSearchParams(location.hash.replace(/^#/, ''));
window.viewer = {
  set(s) {
    if (s.render) renderMode = s.render;
    if (s.worn) { Object.keys(wornState).forEach((n) => wornState[n] = s.worn.includes(n)); }
    if (s.extras !== undefined) showExtras = s.extras;
    if (s.grid !== undefined) state.grid = s.grid;
    if (s.spin !== undefined) state.spin = s.spin;
    if (s.compareSet) state.compareSet = new Set(s.compareSet);
    if (s.compare !== undefined) state.compare = s.compare;
    if (s.item) chooseItem(s.item);
    if (s.model) { state.model = s.model; }
    if (s.ctx) { state.ctx = s.ctx; state.compare = false; }
    if (s.select) selectedElement = { model: state.model, name: s.select };
    syncChecks(); markButtons(); rebuild();
    if (s.camera) setCamera(s.camera.pos, s.camera.target, s.camera.fov);
    renderFrame();
    return true;
  },
  ready() { return Object.values(texCache).every((t) => t.image && t.image.complete); },
  info() { return { item: state.item, model: state.model, ctx: state.ctx, elements: D.models[state.model].json.elements.length }; },
};
chooseItem(params.get('item') || (D.items.find((i) => i.id === 'cloud_lance') || D.items[0]).id);   // opens on the Cloud Lance (owner wants to readjust it); #item=<id>&ctx=<view> overrides
if (params.get('ctx')) { state.ctx = params.get('ctx'); markButtons(); rebuild(); }
requestAnimationFrame(loop);
})();
