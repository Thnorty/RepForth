# Third-party notices

Four separate sets of terms apply to this repository. They are genuinely
different, and the most restrictive one is the exercise imagery — read that
section before enabling the `licensed` flavour.

## Exercise data — MIT

Names, categories, body parts, equipment, targets, muscle groups, and the
multilingual instructions come from
[hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset),
pinned to the commit in [`dataset-version.toml`](dataset-version.toml).

Released by its author under the MIT Licence. Copyright © 2026 Hasan Emir
Yıldırım. The packaged catalog in `core/database/src/main/assets/repforth.db` is
built from that data.

## Exercise imagery — Gym visual, and NOT licensed to you

> **© Gym visual — https://gymvisual.com/**

The thumbnails and animation GIFs are the property of Gym visual. The upstream
dataset redistributes them under a **separate written permission granted to that
project**, at 180×180 resolution, with the attribution above required on every
use.

**That permission is upstream's, not transitive.** Upstream states it plainly:
cloning the repository is not a licence. The same is true here — RepForth
redistributes none of that imagery, and this repository grants you no rights to
it.

What that means in practice:

- The **`placeholder` flavour is the default**, and is the only flavour that can
  be built and distributed from this source without media rights of your own. It
  contains no exercise imagery at all.
- The **`licensed` flavour** downloads imagery at runtime from the pinned commit.
  Building it for anything beyond local evaluation requires you to obtain your
  own licence directly from Gym visual, under their
  [Terms & Conditions of Use](https://gymvisual.com/content/3-terms-and-conditions-of-use).
- No image or GIF is committed to this repository. `dataset/media-manifest.json`
  holds URLs, SHA-256 hashes and byte sizes — references, not bytes.
- Wherever imagery is shown, the attribution above must be shown with it, and the
  180×180 resolution limit respected.

## Fonts — SIL Open Font License 1.1

- **Archivo** — display type and the numeric scale. Licence text:
  [`licenses/Archivo-OFL.txt`](licenses/Archivo-OFL.txt)
- **Manrope** — interface type. Licence text:
  [`licenses/Manrope-OFL.txt`](licenses/Manrope-OFL.txt)

Both ship as static weights in `core/designsystem/src/main/res/font/`. Only the
weights the type scale actually uses are packaged.

## Body-map artwork — part of this project

The front and back silhouettes in `art/bodymap/` were authored for RepForth and
are covered by whatever licence this project adopts. They are not derived from
the dataset or from any third-party anatomy illustration.

## Application code

See [`README.md`](README.md#licence). A project licence has not yet been chosen.
