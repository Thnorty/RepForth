#!/usr/bin/env bash
# Fails if the pinned dataset commit is written anywhere but dataset-version.toml.
#
# §6 step 1 makes that file the single source of truth: every import path and
# media URL derives from it, so metadata and media cannot drift to different
# commits. A second copy of the SHA is how that guarantee quietly stops holding.
#
# Runs in CI and locally: tools/verify-dataset-pin.sh

set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 2

PIN_FILE="dataset-version.toml"

if [ ! -f "$PIN_FILE" ]; then
  echo "verify-dataset-pin: $PIN_FILE is missing." >&2
  exit 1
fi

SHA=$(grep -oE '^commit *= *"[0-9a-f]{40}"' "$PIN_FILE" | grep -oE '[0-9a-f]{40}')
if [ -z "$SHA" ]; then
  echo "verify-dataset-pin: no 40-character commit found in $PIN_FILE." >&2
  exit 1
fi

# Only tracked files are checked. Build output and the downloaded dataset are
# not part of the repository and legitimately contain the SHA in their paths.
#
# Two exclusions, both for the same reason: these files are GENERATED from the
# pin and stamp which commit produced them. That is provenance, not a second
# source of truth -- it is what lets the vocabulary test notice a stale file, and
# what ties a media hash to the bytes it was computed from. Everything under
# dataset/ is importer output; nothing there is hand-written.
#
# Note for whoever adds the next generated artifact: `git grep` only sees TRACKED
# files, so run this AFTER staging. Running it on an unstaged file passes and
# tells you nothing.
OFFENDERS=$(git grep -l --fixed-strings "$SHA" -- . \
  ":(exclude)$PIN_FILE" \
  ":(exclude)tools/verify-dataset-pin.sh" \
  ":(exclude)dataset/" \
  ":(exclude)core/model/src/test/resources/dataset-vocabulary.json" \
  || true)

if [ -n "$OFFENDERS" ]; then
  echo "::error::The dataset commit is hard-coded outside $PIN_FILE:" >&2
  echo "$OFFENDERS" | sed 's/^/  /' >&2
  echo "" >&2
  echo "Derive it from $PIN_FILE instead (§6, step 1)." >&2
  exit 1
fi

echo "verify-dataset-pin: ${SHA:0:12} is recorded only in $PIN_FILE."
