/* Minecraft block/item model json -> three.js (shared by the overlay generators). Same rules as tools/viewer_app.js:
 * uv is in 0..16 space with v measured from the TOP of the png (so textures must use flipY=false), faces without uv use the game's default rule.
 * window.McModel.build(THREE, json, keyOf, matFor) -> { group, box } in model units (16 = one block). */
(function () {
  'use strict';
  const DEG = Math.PI / 180;
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
  function build(THREE, json, keyOf, matFor) {
    const elementMatrix = (el) => {
      const m = new THREE.Matrix4(); const r = el.rotation; if (!r) return m;
      const o = r.origin, a = (r.angle || 0) * DEG, rm = new THREE.Matrix4();
      if (r.axis === 'x') rm.makeRotationX(a); else if (r.axis === 'y') rm.makeRotationY(a); else rm.makeRotationZ(a);
      return m.makeTranslation(o[0], o[1], o[2]).multiply(rm).multiply(new THREE.Matrix4().makeTranslation(-o[0], -o[1], -o[2]));
    };
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
  window.McModel = { build };
})();
