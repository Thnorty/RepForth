"""Extracts the categorical vocabulary from the pinned dataset.

The output is committed so the model tests are hermetic: they assert that every
value the dataset actually uses maps to a Kotlin constant, without needing the
17 MB dataset on the test host. Re-run this whenever the pin moves -- a new
upstream value then fails the tests instead of silently becoming an "unknown".

Usage: python tools/extract-vocabulary.py
"""
import collections
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "core/model/src/test/resources/dataset-vocabulary.json")


def pin(key):
    text = io.open(os.path.join(ROOT, "dataset-version.toml"), encoding="utf-8").read()
    m = re.search(r'^%s *= *"([^"]*)"' % key, text, re.M)
    if not m:
        sys.exit("extract-vocabulary: no '%s' in dataset-version.toml" % key)
    return m.group(1)


def main():
    commit = pin("commit")
    path = os.path.join(ROOT, ".dataset-cache", commit, "data/exercises.json")
    if not os.path.exists(path):
        sys.exit("extract-vocabulary: run tools/fetch-dataset.sh first (%s)" % path)

    records = json.load(io.open(path, encoding="utf-8"))

    muscles = collections.Counter()
    for r in records:
        muscles[r["target"]] += 1
        muscles[r["muscle_group"]] += 1
        for m in r["secondary_muscles"]:
            muscles[m] += 1

    vocabulary = {
        "commit": commit,
        "recordCount": len(records),
        "bodyPart": sorted({r["body_part"] for r in records}),
        "equipment": sorted({r["equipment"] for r in records}),
        "muscle": sorted(muscles),
        # Kept apart so a term used only as a secondary muscle is visible as
        # such; the app filters on all three, but they are not interchangeable.
        "muscleByRole": {
            "target": sorted({r["target"] for r in records}),
            "muscleGroup": sorted({r["muscle_group"] for r in records}),
            "secondary": sorted({m for r in records for m in r["secondary_muscles"]}),
        },
        "attribution": sorted({r["attribution"] for r in records}),
    }

    io.open(OUT, "w", encoding="utf-8", newline="\n").write(
        json.dumps(vocabulary, indent=2, ensure_ascii=False, sort_keys=True) + "\n"
    )
    print("wrote %s" % os.path.relpath(OUT, ROOT))
    for k in ("bodyPart", "equipment", "muscle"):
        print("  %-10s %d" % (k, len(vocabulary[k])))


if __name__ == "__main__":
    main()
