"""Builds the app's catalog artifacts from the pinned upstream dataset.

Produces three things:

  core/database/src/main/assets/repforth.db   the prepackaged Room database
  dataset/media-manifest.json                 media URLs, hashes and sizes
  dataset/import-report.json                  what the import actually did

The database schema is not written here. It is read from the schema Room itself
exported, including the identity hash, so the packaged file cannot drift from the
entity definitions -- if someone changes an entity without re-running this, Room
refuses the asset at runtime rather than reading a mismatched table.

Nothing downloaded is committed. The metadata is MIT, the media is not (§6), so
this emits hashes and URLs and never the bytes.

Usage: python tools/import-dataset.py
"""
import hashlib
import io
import json
import os
import re
import sqlite3
import sys

try:
    from jsonschema import Draft202012Validator
except ImportError:
    sys.exit("import-dataset: needs jsonschema (pip install jsonschema)")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA_DIR = os.path.join(ROOT, "core/database/schemas/com.repforth.core.database.RepForthDatabase")
DB_OUT = os.path.join(ROOT, "core/database/src/main/assets/repforth.db")
MANIFEST_OUT = os.path.join(ROOT, "dataset/media-manifest.json")
MANIFEST_ASSET_OUT = os.path.join(ROOT, "core/media/src/main/assets/media-manifest.json")
REPORT_OUT = os.path.join(ROOT, "dataset/import-report.json")
VOCABULARY = os.path.join(ROOT, "core/model/src/test/resources/dataset-vocabulary.json")

# §6 step 5: the dataset ships ten languages; the app keeps the two it supports.
LANGUAGES = ("en", "tr")


def fail(message):
    sys.exit("import-dataset: " + message)


def pin(key):
    text = io.open(os.path.join(ROOT, "dataset-version.toml"), encoding="utf-8").read()
    m = re.search(r'^%s *= *"([^"]*)"' % key, text, re.M)
    if not m:
        fail("no '%s' in dataset-version.toml" % key)
    return m.group(1)


def pin_int(key):
    text = io.open(os.path.join(ROOT, "dataset-version.toml"), encoding="utf-8").read()
    m = re.search(r"^%s *= *(\d+)" % key, text, re.M)
    if not m:
        fail("no '%s' in dataset-version.toml" % key)
    return int(m.group(1))


def load(path):
    return json.load(io.open(path, encoding="utf-8"))


def normalise(text):
    """§6 step 4: collapse whitespace, never touch the meaning."""
    return " ".join(text.split())


def validate_against_upstream(records, schema):
    """Checks the data against its own author's contract rather than our reading
    of it. Reports at most a few failures: a schema-wide break produces 1,324
    identical messages, and the first few are what identifies it."""
    validator = Draft202012Validator(schema)
    errors = sorted(validator.iter_errors(records), key=lambda e: list(e.absolute_path))
    if errors:
        for e in errors[:5]:
            print("  schema: %s -> %s" % ("/".join(str(p) for p in e.absolute_path), e.message))
        fail("%d schema violations in exercises.json" % len(errors))


def check_invariants(records, root, vocabulary, expected_count):
    """The app-level rules §6 step 8 requires CI to fail on."""
    problems = []

    ids = [r["id"] for r in records]
    duplicates = sorted({i for i in ids if ids.count(i) > 1})
    if duplicates:
        problems.append("duplicate exercise ids: %s" % duplicates[:10])

    if len(records) != expected_count:
        problems.append(
            "expected %d records, found %d -- the pin moved or the download truncated"
            % (expected_count, len(records))
        )

    for language in LANGUAGES:
        missing = [r["id"] for r in records if not r["instruction_steps"].get(language)]
        if missing:
            problems.append(
                "%d records have no '%s' instruction steps: %s"
                % (len(missing), language, missing[:10])
            )

    # A categorical value with no Kotlin constant becomes an unknown at runtime
    # and quietly drops the exercise out of every filter naming that value.
    known = {
        "body_part": set(vocabulary["bodyPart"]),
        "equipment": set(vocabulary["equipment"]),
    }
    muscles = set(vocabulary["muscle"])
    for field, allowed in known.items():
        unknown = sorted({r[field] for r in records} - allowed)
        if unknown:
            problems.append("unknown %s values: %s" % (field, unknown))
    used_muscles = {r["target"] for r in records} | {r["muscle_group"] for r in records}
    used_muscles |= {m for r in records for m in r["secondary_muscles"]}
    unknown_muscles = sorted(used_muscles - muscles)
    if unknown_muscles:
        problems.append("unknown muscle values: %s" % unknown_muscles)

    missing_media = [
        r["id"] for r in records
        if not os.path.exists(os.path.join(root, r["image"]))
        or not os.path.exists(os.path.join(root, r["gif_url"]))
    ]
    if missing_media:
        problems.append("%d records reference missing media: %s"
                        % (len(missing_media), missing_media[:10]))

    if problems:
        for p in problems:
            print("  " + p)
        fail("the dataset failed %d invariant(s)" % len(problems))


def build_database(records, schema, out):
    """Creates the packaged database using Room's own DDL."""
    database = schema["database"]
    version = database["version"]

    if os.path.exists(out):
        os.remove(out)
    os.makedirs(os.path.dirname(out), exist_ok=True)

    conn = sqlite3.connect(out)
    cur = conn.cursor()

    for entity in database["entities"]:
        table = entity["tableName"]
        cur.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            cur.execute(index["createSql"].replace("${TABLE_NAME}", table))

    # Room reads room_master_table to confirm the packaged schema is the one this
    # build expects, and user_version to decide whether to migrate. Both come
    # from the exported schema, so neither can disagree with the entities.
    for query in database.get("setupQueries", []):
        cur.execute(query)
    cur.execute("PRAGMA user_version = %d" % version)

    cur.executemany(
        "INSERT INTO exercise (id, name, body_part, target, muscle_group, equipment) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        [
            (r["id"], normalise(r["name"]), r["body_part"], r["target"],
             r["muscle_group"], r["equipment"])
            for r in records
        ],
    )

    cur.executemany(
        "INSERT INTO exercise_secondary_muscle (exercise_id, muscle) VALUES (?, ?)",
        # A record may list the same secondary muscle twice; the primary key
        # would reject the duplicate, so it is collapsed here rather than
        # failing an import over a harmless upstream repetition.
        sorted({(r["id"], m) for r in records for m in r["secondary_muscles"]}),
    )

    steps = []
    for r in records:
        for language in LANGUAGES:
            position = 0
            for step in r["instruction_steps"][language]:
                text = normalise(step)
                if not text:
                    continue
                steps.append((r["id"], language, position, text))
                position += 1
    cur.executemany(
        "INSERT INTO exercise_instruction_step (exercise_id, language, position, text) "
        "VALUES (?, ?, ?, ?)",
        steps,
    )

    conn.commit()
    cur.execute("PRAGMA foreign_key_check")
    violations = cur.fetchall()
    if violations:
        fail("%d foreign key violations in the built database" % len(violations))
    conn.execute("VACUUM")
    conn.close()
    return version, len(steps)


def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest(), os.path.getsize(path)


def build_manifest(records, root, repository, commit, media_version, attribution, out):
    """§6 step 7. Paths rather than full URLs per entry: the base is identical
    for all 2,648 of them, and keeping it in one field is what lets §9's move to
    object storage be an edit to one line."""
    entries = []
    for r in records:
        thumb_sha, thumb_bytes = digest(os.path.join(root, r["image"]))
        anim_sha, anim_bytes = digest(os.path.join(root, r["gif_url"]))
        entries.append({
            "id": r["id"],
            "thumbnail": {"path": r["image"], "sha256": thumb_sha, "bytes": thumb_bytes},
            "animation": {"path": r["gif_url"], "sha256": anim_sha, "bytes": anim_bytes},
        })

    manifest = {
        "commit": commit,
        "mediaVersion": media_version,
        "baseUrl": "https://raw.githubusercontent.com/%s/%s/" % (repository, commit),
        "attribution": attribution,
        "entries": entries,
    }
    os.makedirs(os.path.dirname(out), exist_ok=True)
    io.open(out, "w", encoding="utf-8", newline="\n").write(
        json.dumps(manifest, indent=1, ensure_ascii=False) + "\n"
    )
    return entries


def main():
    commit = pin("commit")
    repository = pin("repository")
    media_version = pin_int("version")
    expected_count = pin_int("record_count")

    root = os.path.join(ROOT, ".dataset-cache", commit)
    if not os.path.isdir(root):
        fail("dataset not present; run tools/fetch-dataset.sh")

    records = load(os.path.join(root, "data/exercises.json"))
    upstream_schema = load(os.path.join(root, "data/exercises.schema.json"))
    vocabulary = load(VOCABULARY)

    print("validating %d records against the upstream schema" % len(records))
    validate_against_upstream(records, upstream_schema)
    check_invariants(records, root, vocabulary, expected_count)

    versions = [int(os.path.splitext(f)[0]) for f in os.listdir(SCHEMA_DIR) if f.endswith(".json") and os.path.splitext(f)[0].isdigit()]
    if not versions:
        fail("no exported Room schemas in %s" % SCHEMA_DIR)
    latest_version = max(versions)
    room_schema = load(os.path.join(SCHEMA_DIR, "%d.json" % latest_version))
    version, step_count = build_database(records, room_schema, DB_OUT)
    print("built %s (v%d, %d steps, %.1f MB)"
          % (os.path.relpath(DB_OUT, ROOT), version, step_count,
             os.path.getsize(DB_OUT) / 1e6))

    attributions = {r["attribution"] for r in records}
    if len(attributions) != 1:
        fail("expected one attribution string, found %d" % len(attributions))

    print("hashing media for %d records" % len(records))
    entries = build_manifest(records, root, repository, commit, media_version,
                             attributions.pop(), MANIFEST_OUT)
    os.makedirs(os.path.dirname(MANIFEST_ASSET_OUT), exist_ok=True)
    with open(MANIFEST_OUT, "r", encoding="utf-8") as src, open(MANIFEST_ASSET_OUT, "w", encoding="utf-8", newline="\n") as dst:
        dst.write(src.read())
    total_bytes = sum(e["thumbnail"]["bytes"] + e["animation"]["bytes"] for e in entries)
    print("wrote %s and %s (%d entries, %.1f MB of media described)"
          % (os.path.relpath(MANIFEST_OUT, ROOT), os.path.relpath(MANIFEST_ASSET_OUT, ROOT), len(entries), total_bytes / 1e6))

    report = {
        "commit": commit,
        "records": len(records),
        "languagesKept": list(LANGUAGES),
        "languagesAvailable": sorted(records[0]["instructions"].keys()),
        "instructionSteps": step_count,
        "databaseVersion": version,
        "databaseBytes": os.path.getsize(DB_OUT),
        "mediaFiles": len(entries) * 2,
        "mediaBytes": total_bytes,
        # §6 asks for a record of what normalisation did, so an upstream change
        # can be reviewed rather than silently absorbed.
        "normalisation": "whitespace collapsed in names and instruction steps; "
                         "empty steps dropped; duplicate secondary muscles collapsed",
    }
    io.open(REPORT_OUT, "w", encoding="utf-8", newline="\n").write(
        json.dumps(report, indent=1) + "\n"
    )
    print("wrote %s" % os.path.relpath(REPORT_OUT, ROOT))


if __name__ == "__main__":
    main()
