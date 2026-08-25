#!/usr/bin/env python3
"""Make Super Chat sticker assets background-free and tight-cropped.

Reads  app/src/main/assets/poses/stickers/pose_*.webp  (RGB, sheet-cell slices)
and rewrites each file in place as RGBA WebP where:
  - the sheet-cell background (region-grown from the image border) is transparent
  - the image is tight-cropped to the character with a small padding

Region growing compares each candidate pixel against its neighbouring
background pixel, so gradient backgrounds (dark navy, purple, white) are
handled without a global threshold.
"""
import os
import sys
from collections import deque

import numpy as np
from PIL import Image

STICKER_DIR = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets", "poses", "stickers"
)
TOLERANCE = 30  # max per-channel-ish distance between neighbouring bg pixels
PAD = 6


def background_mask(rgb: np.ndarray) -> np.ndarray:
    """Boolean mask, True where the pixel belongs to the border-connected background."""
    h, w, _ = rgb.shape
    bg = np.zeros((h, w), dtype=bool)
    visited = np.zeros((h, w), dtype=bool)
    q = deque()

    def push(y, x):
        if not visited[y, x]:
            visited[y, x] = True
            q.append((y, x))

    for x in range(w):
        push(0, x)
        push(h - 1, x)
    for y in range(h):
        push(y, 0)
        push(y, w - 1)

    while q:
        y, x = q.popleft()
        bg[y, x] = True
        c = rgb[y, x]
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx]:
                n = rgb[ny, nx]
                if abs(int(n[0]) - int(c[0])) <= TOLERANCE and \
                   abs(int(n[1]) - int(c[1])) <= TOLERANCE and \
                   abs(int(n[2]) - int(c[2])) <= TOLERANCE:
                    push(ny, nx)
    return bg


def process(path: str) -> tuple:
    im = Image.open(path).convert("RGB")
    rgb = np.asarray(im).astype(np.int16)
    h, w, _ = rgb.shape

    bg = background_mask(rgb)

    alpha = np.where(bg, 0, 255).astype(np.uint8)

    # Feather the cutout edge by 1px so there is no hard jaggies ring.
    a_img = Image.fromarray(alpha, "L").filter(__import__("PIL.ImageFilter", fromlist=["GaussianBlur"]).GaussianBlur(0.8))
    alpha = np.asarray(a_img)

    ys, xs = np.where(alpha > 12)
    if len(ys) == 0:
        return (path, w, h, 0, 0)  # nothing left; leave file untouched
    top, bottom = max(ys.min() - PAD, 0), min(ys.max() + PAD + 1, h)
    left, right = max(xs.min() - PAD, 0), min(xs.max() + PAD + 1, w)

    out = np.dstack([np.asarray(im), alpha])[top:bottom, left:right]
    Image.fromarray(out, "RGBA").save(path, "WEBP", quality=88)
    return (path, w, h, right - left, bottom - top)


def main():
    d = os.path.abspath(STICKER_DIR)
    files = sorted(f for f in os.listdir(d) if f.startswith("pose_") and f.endswith(".webp"))
    print(f"processing {len(files)} stickers ...")
    for i, f in enumerate(files, 1):
        p = os.path.join(d, f)
        _, ow, oh, nw, nh = process(p)
        if i % 24 == 0 or i == len(files):
            print(f"  {i}/{len(files)}  {f}: {ow}x{oh} -> {nw}x{nh}")
    print("done")


if __name__ == "__main__":
    sys.exit(main())
