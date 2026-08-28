"""Rasterises the body-map SVGs so a human can look at them.

There is no SVG renderer available on this machine (cairo is missing), and the
artwork is the one thing that cannot be checked by asserting things about ids and
byte counts -- it either reads as a body or it does not. So this flattens the
path data into polygons and lets Pillow fill them.

Deliberately minimal: it understands only the path commands the artwork uses, and
fills with the non-zero-ish approximation of treating each subpath as its own
polygon. Good enough to see whether the chest is on the chest.

Usage: python tools/render-bodymap.py
"""
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("render-bodymap: needs Pillow (pip install pillow)")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SVG_NS = "{http://www.w3.org/2000/svg}"
SCALE = 2

PALETTE = {
    "silhouette": (70, 76, 60),
    "neck": (255, 107, 107), "shoulders": (255, 169, 77), "chest": (255, 212, 59),
    "abs": (169, 227, 75), "obliques": (105, 219, 124), "biceps": (56, 217, 169),
    "triceps": (59, 201, 219), "forearms": (77, 171, 247), "quads": (116, 143, 252),
    "hamstrings": (151, 117, 250), "glutes": (218, 119, 242), "lats": (247, 131, 172),
    "traps": (255, 135, 135), "upper-back": (255, 192, 120), "lower-back": (255, 224, 102),
    "lower-legs": (140, 233, 154), "adductors": (99, 230, 190),
    "abductors": (102, 217, 232), "hip-flexors": (145, 167, 255),
}

TOKEN = re.compile(r"([MmLlHhVvCcQqSsTtZz])|(-?\d*\.?\d+(?:[eE][-+]?\d+)?)")


def tokenize(d):
    for cmd, num in TOKEN.findall(d):
        yield ("cmd", cmd) if cmd else ("num", float(num))


def flatten(d, steps=18):
    """Path data -> list of polygons (each a list of (x, y))."""
    polys, pts = [], []
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    prev_ctrl = None
    cmd = None
    stream = list(tokenize(d))
    i = 0

    def bez3(p0, p1, p2, p3):
        for s in range(1, steps + 1):
            t = s / steps
            u = 1 - t
            yield (u*u*u*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t*t*t*p3[0],
                   u*u*u*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t*t*t*p3[1])

    def bez2(p0, p1, p2):
        for s in range(1, steps + 1):
            t = s / steps
            u = 1 - t
            yield (u*u*p0[0] + 2*u*t*p1[0] + t*t*p2[0],
                   u*u*p0[1] + 2*u*t*p1[1] + t*t*p2[1])

    def nums(n):
        nonlocal i
        out = []
        for _ in range(n):
            if i >= len(stream) or stream[i][0] != "num":
                return None
            out.append(stream[i][1]); i += 1
        return out

    while i < len(stream):
        kind, val = stream[i]
        if kind == "cmd":
            cmd = val; i += 1
            if cmd in "Zz":
                if len(pts) > 2:
                    polys.append(pts)
                pts = []
                cur = start
                continue
        rel = cmd.islower()
        c = cmd.upper()

        if c == "M":
            a = nums(2)
            if a is None: break
            cur = (cur[0] + a[0], cur[1] + a[1]) if rel else (a[0], a[1])
            if len(pts) > 2:
                polys.append(pts)
            pts = [cur]; start = cur
            cmd = "l" if rel else "L"
        elif c == "L":
            a = nums(2)
            if a is None: break
            cur = (cur[0] + a[0], cur[1] + a[1]) if rel else (a[0], a[1])
            pts.append(cur)
        elif c == "H":
            a = nums(1)
            if a is None: break
            cur = (cur[0] + a[0], cur[1]) if rel else (a[0], cur[1])
            pts.append(cur)
        elif c == "V":
            a = nums(1)
            if a is None: break
            cur = (cur[0], cur[1] + a[0]) if rel else (cur[0], a[0])
            pts.append(cur)
        elif c == "C":
            a = nums(6)
            if a is None: break
            p1 = (cur[0]+a[0], cur[1]+a[1]) if rel else (a[0], a[1])
            p2 = (cur[0]+a[2], cur[1]+a[3]) if rel else (a[2], a[3])
            p3 = (cur[0]+a[4], cur[1]+a[5]) if rel else (a[4], a[5])
            pts.extend(bez3(cur, p1, p2, p3)); prev_ctrl = p2; cur = p3
        elif c == "S":
            a = nums(4)
            if a is None: break
            p1 = (2*cur[0]-prev_ctrl[0], 2*cur[1]-prev_ctrl[1]) if prev_ctrl else cur
            p2 = (cur[0]+a[0], cur[1]+a[1]) if rel else (a[0], a[1])
            p3 = (cur[0]+a[2], cur[1]+a[3]) if rel else (a[2], a[3])
            pts.extend(bez3(cur, p1, p2, p3)); prev_ctrl = p2; cur = p3
        elif c == "Q":
            a = nums(4)
            if a is None: break
            p1 = (cur[0]+a[0], cur[1]+a[1]) if rel else (a[0], a[1])
            p2 = (cur[0]+a[2], cur[1]+a[3]) if rel else (a[2], a[3])
            pts.extend(bez2(cur, p1, p2)); prev_ctrl = p1; cur = p2
        elif c == "T":
            a = nums(2)
            if a is None: break
            p1 = (2*cur[0]-prev_ctrl[0], 2*cur[1]-prev_ctrl[1]) if prev_ctrl else cur
            p2 = (cur[0]+a[0], cur[1]+a[1]) if rel else (a[0], a[1])
            pts.extend(bez2(cur, p1, p2)); prev_ctrl = p1; cur = p2
        else:
            i += 1

    if len(pts) > 2:
        polys.append(pts)
    return polys


def render(path, out):
    root = ET.parse(path).getroot()
    vb = [float(v) for v in root.get("viewBox").replace(",", " ").split()]
    w, h = int(vb[2] * SCALE), int(vb[3] * SCALE)
    img = Image.new("RGB", (w, h), (26, 31, 18))
    draw = ImageDraw.Draw(img)

    stats = []
    for el in root.iter(SVG_NS + "path"):
        ident = el.get("id") or ""
        colour = PALETTE.get(ident, (200, 200, 200))
        xs, ys = [], []
        for poly in flatten(el.get("d", "")):
            scaled = [(x * SCALE, y * SCALE) for x, y in poly]
            draw.polygon(scaled, fill=colour)
            xs += [p[0] for p in poly]; ys += [p[1] for p in poly]
        if xs and ident != "silhouette":
            stats.append((ident, min(xs), min(ys), max(xs), max(ys)))
    img.save(out)
    return stats


def main():
    for view in ("front", "back"):
        src = os.path.join(ROOT, "art/bodymap/body-%s.svg" % view)
        dst = os.path.join(ROOT, "build/body-%s.png" % view)
        stats = render(src, dst)
        print("=== %s -> %s ===" % (os.path.basename(src), os.path.relpath(dst, ROOT)))
        for ident, x0, y0, x1, y1 in sorted(stats, key=lambda s: s[2]):
            print("   %-12s y %5.0f..%-5.0f  x %5.0f..%-5.0f" % (ident, y0, y1, x0, x1))
        print()


if __name__ == "__main__":
    main()
