#!/usr/bin/env bash
# Downloads the upstream dataset at the commit pinned in dataset-version.toml.
#
# The whole repository arrives as one tarball rather than as 2,648 individual
# media requests: it is one connection instead of thousands, and it cannot
# half-succeed into a partial media set that later looks like a complete one.
#
# Nothing downloaded here is ever committed. The metadata is MIT, but the images
# and videos carry a separate visual copyright exception (§6), so the working
# directory is gitignored and the importer emits URLs and hashes, not files.
#
# Usage: tools/fetch-dataset.sh [destination]   (default: .dataset-cache)

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

PIN_FILE="dataset-version.toml"
DEST="${1:-.dataset-cache}"

value() { grep -oE "^$1 *= *\"[^\"]*\"" "$PIN_FILE" | head -1 | sed -E 's/.*"([^"]*)".*/\1/'; }

REPO=$(value repository)
SHA=$(value commit)
[ -n "$REPO" ] && [ -n "$SHA" ] || { echo "fetch-dataset: could not read the pin from $PIN_FILE" >&2; exit 1; }

TARGET="$DEST/$SHA"
if [ -d "$TARGET" ]; then
  echo "fetch-dataset: already present at $TARGET"
  exit 0
fi

mkdir -p "$DEST"
TARBALL="$DEST/${SHA}.tar.gz"
URL="https://codeload.github.com/$REPO/tar.gz/$SHA"

echo "fetch-dataset: $REPO @ ${SHA:0:12}"
echo "fetch-dataset: GET $URL"
curl -fsSL --retry 3 --retry-delay 2 -o "$TARBALL" "$URL"

# Extract into a commit-named directory, so two pins can coexist while a
# dataset diff is being reviewed (§18) and neither can be mistaken for the other.
mkdir -p "$TARGET"
tar -xzf "$TARBALL" -C "$TARGET" --strip-components=1
rm -f "$TARBALL"

echo "fetch-dataset: extracted to $TARGET"
echo "  exercises.json : $(wc -c < "$TARGET/data/exercises.json") bytes"
echo "  images         : $(find "$TARGET/images" -type f | wc -l) files"
echo "  videos         : $(find "$TARGET/videos" -type f | wc -l) files"
