#!/usr/bin/env python3
"""Make Super Chat sticker assets background-free and tight-cropped.

Reads  app/src/main/assets/poses/stickers/pose_*.webp  and rewrites each file in place as
RGBA WebP where:
  - the sheet-cell background is transparent
  - interior holes are opaque (colors matching the bg *inside* the character stay)
  - only the largest connected component survives (sheet numbers / text dropped)
  - the image is tight-cropped to the character with a small padding

Algorithm (per image):
  1. Estimate the background color as the median of all border pixels — robust even when
     the character touches one edge.
  2. Foreground = pixels whose RGB distance from that color exceeds a threshold; this
     handles flat, gradient, dark-navy, purple and white sheet backgrounds alike.
  3. Keep only the largest connected foreground component (scipy.ndimage.label).
  4. Fill holes so interior pixels cut off from the border stay part of the character.
  5. Feather alpha slightly, then tight-crop with padding.

Requires: pillow, numpy, scipy.
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

STICKER_DIR = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets", "poses", "stickers"
)
DIST_THRESHOLD = 45  # RGB euclidean distance from bg color marking foreground
PAD = 4


def process(path: str) -> tuple:
    im = Image.open(path).convert("RGBA")
    rgba = np.asarray(im)
    rgb = rgba[..., :3].astype(np.int16)
    h, w = rgb.shape[:2]

    # 1) Background color = median of border pixels
    border = np.concatenate([rgb[0, :, :], rgb[-1, :, :], rgb[:, 0, :], rgb[:, -1, :]])
    bg = np.median(border, axis=0)

    # 2) Foreground mask
    dist = np.sqrt(((rgb - bg) ** 2).sum(axis=-1))
    fg = dist > DIST_THRESHOLD

    # 3) Keep the largest connected component only
    lab, n = ndimage.label(fg)
    if n > 1:
        sizes = ndimage.sum(fg, lab, range(1, n + 1))
        fg = lab == (int(sizes.argmax()) + 1)

    # 4) Fill interior holes
    fg = ndimage.binary_fill_holes(fg)

    # 5) Feathered alpha
    alpha = ndimage.gaussian_filter(fg.astype(float), sigma=1.2)
    alpha = np.clip(alpha * 255, 0, 255).astype(np.uint8)

    ys, xs = np.where(alpha > 8)
    if len(ys) == 0:
        return (path, w, h, w, h)  # nothing found; leave untouched
    top, bottom = max(ys.min() - PAD, 0), min(ys.max() + PAD + 1, h)
    left, right = max(xs.min() - PAD, 0), min(xs.max() + PAD + 1, w)

    out = np.dstack([rgba[..., :3].astype(np.uint8), alpha])[top:bottom, left:right]
    Image.fromarray(out, "RGBA").save(path, "WEBP", quality=88)
    return (path, w, h, right - left, bottom - top)


def main():
    d = os.path.abspath(STICKER_DIR)
    files = sorted(f for f in os.listdir(d) if f.startswith("pose_") and f.endswith(".webp"))
    print(f"processing {len(files)} stickers ...")
    for i, f in enumerate(files, 1):
        _, ow, oh, nw, nh = process(os.path.join(d, f))
        if i % 24 == 0 or i == len(files):
            print(f"  {i}/{len(files)}  {f}: {ow}x{oh} -> {nw}x{nh}")
    print("done")


if __name__ == "__main__":
    sys.exit(main())
