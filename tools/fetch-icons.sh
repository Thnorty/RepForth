#!/usr/bin/env bash
# fetch-icons.sh — download the Material Symbols this project uses and convert
# them to Android vector drawables.
#
# The set is Material Symbols Rounded, Apache 2.0, which is what the design
# system specifies (design-system/readme.md, ICONOGRAPHY). The names come from
# icons.txt beside this script rather than being rediscovered each run, so the
# set is reviewable in a diff.
#
# Rerunning is safe: it overwrites, and the output is deterministic.
#
# Usage:
#   tools/fetch-icons.sh
#
# Env:
#   ICON_STYLE   materialsymbolsrounded (default) | materialsymbolsoutlined
#   ICON_OUT     output directory (default: core/designsystem/src/main/res/drawable)

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
STYLE="${ICON_STYLE:-materialsymbolsrounded}"
OUT="${ICON_OUT:-$ROOT/core/designsystem/src/main/res/drawable}"
LIST="$HERE/icons.txt"

if [ ! -f "$LIST" ]; then
  echo "fetch-icons: no icon list at $LIST" >&2
  exit 2
fi

mkdir -p "$OUT"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fetched=0
failed=()

while read -r name; do
  # Blank lines and comments, so the list can explain itself.
  case "$name" in ''|\#*) continue ;; esac

  url="https://fonts.gstatic.com/s/i/short-term/release/$STYLE/$name/default/24px.svg"
  if ! curl -fsSL "$url" -o "$WORK/$name.svg"; then
    failed+=("$name")
    continue
  fi
  fetched=$((fetched + 1))
done < "$LIST"

if [ ${#failed[@]} -gt 0 ]; then
  echo "fetch-icons: could not download: ${failed[*]}" >&2
  echo "fetch-icons: a name that 404s is usually a symbol that was renamed upstream." >&2
  exit 1
fi

python "$HERE/svg-to-vector.py" "$WORK" "$OUT"

echo "fetch-icons: $fetched icons -> $OUT"
