"""Builds the README's screenshots from the app's own screenshot goldens.

Writes docs/screenshots/*.png. Run after re-recording goldens:

    ./gradlew test -Proborazzi.record=true && python tools/readme-shots.py

## Why the goldens and not a phone

Three reasons, and the first two are not about effort.

**A device screenshot of the maintainer's phone publishes their training
history.** Body data, weights, what they did and when. A public README is a bad
place for it, and a fixture says the same thing about the app without saying
anything about a person.

**The exercise imagery is not ours to redistribute** (see NOTICE.md). Every
golden renders with `MediaRef.Unavailable`, so what appears is the app's own
placeholder icon and not a frame of Gym visual's artwork. A phone screenshot of
a real workout would put licensed pixels in the repository.

And the goldens are regenerated whenever the UI changes, so pointing the README
at them means it cannot quietly show a version of the app that no longer exists.

## What this does to them

Trims the flat background off the bottom, because a screen with little content
is centred in a tall frame and reads as a broken image at README size. Rounds
the corners so a tile reads as a device rather than a crop, and halves the
resolution — 1078px wide is four times what GitHub will draw.

**Tiles are not padded to a common height**, which was the first version and was
worse: padding every screen up to the tallest put the dead space straight back
into exactly the screens the trim had just rescued. A gallery of different
heights is what a screenshot gallery looks like; a tall image half full of
nothing looks broken.
"""
import io
import os
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("readme-shots: needs pillow (pip install pillow)")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "docs", "screenshots")

# Golden -> published name. Chosen for what they say about the app rather than
# for coverage: the screens a reader has to see to know whether they want this.
PHONE_SHOTS = [
    ("feature/builder/src/test/screenshots/coach-en.png", "coach.png"),
    ("feature/session/src/test/screenshots/session-resting-en.png", "rest.png"),
    ("feature/session/src/test/screenshots/session-adjust-en.png", "progression.png"),
    ("feature/history/src/test/screenshots/progress-en.png", "progress.png"),
]

# The watch is round, and gets a round mask rather than the phones' corner
# radius: a circular face inside a rounded square reads as a picture of a watch
# on a card, which is one frame more than the strip needs.
WATCH_SHOTS = [
    ("wear/src/test/screenshots/pager.png", "watch-set.png"),
    ("wear/src/test/screenshots/rest.png", "watch-rest.png"),
]

# Half of the golden's 1078. Still twice what GitHub draws in a three-column
# table, which is what keeps it sharp on a dense display.
PHONE_WIDTH = 539
WATCH_WIDTH = 360

# Breathing room under the last element, in source pixels, so a trimmed screen
# does not look guillotined.
BOTTOM_MARGIN = 48

CORNER_RADIUS = 28


def background_of(image):
    """The screen's own background, read from a corner rather than assumed.

    The app has a light theme too, and a hard-coded dark value would trim
    nothing at all on a light golden -- silently, leaving the dead space this
    exists to remove.
    """
    return image.getpixel((2, image.height - 2))


def trim_bottom(image):
    """Drops trailing rows that are entirely the background colour."""
    background = background_of(image)
    pixels = image.load()
    last_content = image.height - 1
    while last_content > 0:
        row_is_empty = all(
            pixels[x, last_content] == background for x in range(0, image.width, 4)
        )
        if not row_is_empty:
            break
        last_content -= 1
    height = min(image.height, last_content + 1 + BOTTOM_MARGIN)
    return image.crop((0, 0, image.width, height))


def rounded(image, radius):
    """Rounds the corners, so a tile reads as a device rather than a crop."""
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, image.width - 1, image.height - 1), radius=radius, fill=255
    )
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    out.paste(image, (0, 0), mask)
    return out


def prepare(paths, width, circular=False):
    """Trims each shot, scales it to a common width, and masks its corners."""
    tiles = []
    for source, name in paths:
        full = os.path.join(ROOT, source)
        if not os.path.isfile(full):
            sys.exit("readme-shots: missing golden %s -- record the goldens first" % source)
        image = Image.open(full).convert("RGB")
        background = background_of(image)
        # A round face is already exactly as tall as it is wide, and trimming it
        # would eat the bottom of the circle rather than any dead space.
        trimmed = image if circular else trim_bottom(image)
        scale = width / trimmed.width
        resized = trimmed.resize(
            (width, max(1, round(trimmed.height * scale))), Image.LANCZOS
        )
        tiles.append((name, resized, background))

    written = []
    for name, tile, _ in tiles:
        out = os.path.join(OUT_DIR, name)
        radius = tile.width // 2 if circular else CORNER_RADIUS
        rounded(tile, radius).save(out, optimize=True)
        written.append((name, tile.width, tile.height))
    return written


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    written = prepare(PHONE_SHOTS, PHONE_WIDTH)
    written += prepare(WATCH_SHOTS, WATCH_WIDTH, circular=True)
    for name, w, h in written:
        print("docs/screenshots/%s  %dx%d" % (name, w, h))


if __name__ == "__main__":
    main()
