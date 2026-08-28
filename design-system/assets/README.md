# assets/

**Empty on purpose.** The brief that created this design system came with no files —
no logo, no icon set, no exercise photography or illustration. Nothing here is invented:
drawing a mark or generating imagery from memory would be worse than an honest gap.

What goes where when real material arrives:

| Path | Contents |
| --- | --- |
| `assets/logo.svg` | The RepForth mark. Until it exists, set the name in Archivo 800 (see guidelines/brand-wordmark card) |
| `assets/media/` | Exercise media, strictly 1:1 (square). Referenced via `.rf-media-square` |
| `assets/fonts/` | Self-hosted webfont binaries. Replace the Google Fonts `@import`s in `tokens/fonts.css` with local `@font-face` rules |

Icons are **not** stored here: RepForth uses the Material Symbols Rounded variable icon font,
loaded from Google Fonts in `tokens/fonts.css` and used through the `Icon` component or the
`.rf-icon` class. See ICONOGRAPHY in the root readme.
