#!/usr/bin/env python3
"""Process the user-uploaded single-character sticker PNGs.

Each uploaded file is a 2048x2048 RGB image of ONE pose on a solid background.
This script:
  1. Estimates the background colour as the median of all border pixels.
  2. Builds a foreground mask (RGB distance from bg > threshold).
  3. Keeps the largest connected component and fills interior holes.
  4. Feathers alpha slightly, tight-crops with padding.
  5. Resizes so the longest side is <= 512 px (sticker display size).
  6. Saves as app/src/main/assets/poses/stickers/pose_NNN.webp continuing from
     the highest existing pose number, and deletes the source PNG.

Requires: pillow, numpy, scipy.
"""
import glob
import os
import re

import numpy as np
from PIL import Image
from scipy import ndimage

STICKER_DIR = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets", "poses", "stickers"
)
DIST_THRESHOLD = 45
PAD = 8
MAX_SIDE = 512


def next_pose_number(directory: str) -> int:
    highest = 0
    for f in os.listdir(directory):
        m = re.match(r"^pose_(\d+)\.webp$", f)
        if m:
            highest = max(highest, int(m.group(1)))
    return highest + 1


def process(path: str) -> np.ndarray:
    im = Image.open(path).convert("RGBA")
    rgba = np.asarray(im)
    rgb = rgba[..., :3].astype(np.int16)

    # 1) Background colour = median of border pixels
    border = np.concatenate([rgb[0, :, :], rgb[-1, :, :], rgb[:, 0], rgb[:, -1]])
    bg = np.median(border, axis=0)

    # 2) Foreground mask
    dist = np.sqrt(((rgb - bg) ** 2).sum(axis=-1))
    fg = dist > DIST_THRESHOLD

    # 3) Largest connected component only
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
    top, bottom = max(ys.min() - PAD, 0), min(ys.max() + PAD + 1, fg.shape[0])
    left, right = max(xs.min() - PAD, 0), min(xs.max() + PAD + 1, fg.shape[1])

    out = np.dstack([rgba[..., :3].astype(np.uint8), alpha])[top:bottom, left:right]

    # Resize so longest side <= MAX_SIDE
    h, w = out.shape[:2]
    scale = min(1.0, MAX_SIDE / max(h, w))
    if scale < 1.0:
        new_size = (max(1, int(w * scale)), max(1, int(h * scale)))
        out = np.asarray(
            Image.fromarray(out, "RGBA").resize(new_size, Image.LANCZOS)
        )
    return out


def main():
    d = os.path.abspath(STICKER_DIR)
    pngs = sorted(glob.glob(os.path.join(d, "*.png")))
    print(f"found {len(pngs)} uploaded PNGs")
    num = next_pose_number(d)
    for p in pngs:
        try:
            arr = process(p)
        except Exception as e:
            print(f"  FAILED {os.path.basename(p)}: {e}")
            continue
        out_path = os.path.join(d, f"pose_{num:03d}.webp")
        Image.fromarray(arr, "RGBA").save(out_path, "WEBP", quality=88)
        kb = os.path.getsize(out_path) / 1024
        print(f"  {os.path.basename(p)} -> pose_{num:03d}.webp ({arr.shape[1]}x{arr.shape[0]}, {kb:.0f} KB)")
        os.remove(p)
        num += 1
    print("done")


if __name__ == "__main__":
    main()
