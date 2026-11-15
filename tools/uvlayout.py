"""
UV auto-layout.  Every face of every element gets its own rectangle in the model's texture atlas at
1 texel per model unit (= vanilla pixel density: 16 texels per block).  Atlas sizes are powers of two.

The UVs written into the JSON are in Minecraft's 0..16 space:  u = texel_x / atlas_width * 16.

Run on one model or a group of models that share a texture (e.g. the bow and its 3 pulling states).
"""
import math

from common import FACES, face_texel_size

GUTTER = 1


def collect_faces(models):
    """Unique (element name, face, w, h) rectangles needed by a group of models (identical ones are shared)."""
    wanted = {}
    for m in models:
        for el in m["elements"]:
            for face in el["faces"]:
                w, h = face_texel_size(face, el["from"], el["to"])
                key = (el["name"], face, w, h)
                wanted[key] = (w, h)
    return wanted


def pack(rects, atlas_w, atlas_h):
    """Shelf packer. rects: {key: (w,h)} -> {key: (x,y)} or None if it does not fit."""
    order = sorted(rects.items(), key=lambda kv: (-kv[1][1], -kv[1][0], kv[0]))
    placed = {}
    x = y = shelf_h = 0
    for key, (w, h) in order:
        if x + w > atlas_w:
            x = 0
            y += shelf_h + GUTTER
            shelf_h = 0
        if y + h > atlas_h or w > atlas_w:
            return None
        placed[key] = (x, y)
        x += w + GUTTER
        shelf_h = max(shelf_h, h)
    return placed


def layout_group(models, min_size=32, max_size=512):
    """Assigns 'uv' to every face of every model in the group; returns (atlas_w, atlas_h)."""
    rects = collect_faces(models)
    size = min_size
    while size <= max_size:
        placed = pack(rects, size, size)
        if placed is not None:
            break
        size *= 2
    else:
        raise RuntimeError("texture atlas would exceed %d px" % max_size)
    # try to shave the height (non-square atlas) while keeping power-of-two sizes
    h = size
    while h > min_size and pack(rects, size, h // 2) is not None:
        h //= 2
    placed = pack(rects, size, h)
    for m in models:
        for el in m["elements"]:
            for face, fdata in el["faces"].items():
                w, hh = face_texel_size(face, el["from"], el["to"])
                x, y = placed[(el["name"], face, w, hh)]
                fdata["uv"] = [round(x / size * 16, 5), round(y / h * 16, 5), round((x + w) / size * 16, 5), round((y + hh) / h * 16, 5)]
        m["texture_size"] = [size, h]
    return size, h


def face_rect_texels(model, el, face):
    """The face's rectangle in texels (x0, y0, x1, y1) according to the model's uv + texture_size."""
    tw, th = model.get("texture_size", [16, 16])
    u1, v1, u2, v2 = el["faces"][face]["uv"]
    return u1 / 16 * tw, v1 / 16 * th, u2 / 16 * tw, v2 / 16 * th
