#!/usr/bin/env bash
# agy-task.sh — run `agy` headless as a subagent and print ONLY its final report.
#
# Everything the subagent does in between (tool calls, file diffs, its own
# reasoning and chatter) stays inside the child process and never reaches the
# caller's context. What comes back is a one-line telemetry header plus the
# structured report defined in subagent-report.schema.json, which codex-task.sh
# also uses so both subagents answer in the same shape.
#
# Usage:
#   tools/agy-task.sh "<prompt>"
#
# Env overrides:
#   AGY_MODEL    model id            (default: gemini-3.7-flash-medium)
#   AGY_SCHEMA   path to JSON schema (default: tools/subagent-report.schema.json)
#   AGY_TIMEOUT  print-mode timeout  (default: 15m)
#   AGY_RAW      set to 1 to dump the raw agy JSON instead of the formatted report

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODEL="${AGY_MODEL:-gemini-3.7-flash-medium}"
SCHEMA="${AGY_SCHEMA:-$HERE/subagent-report.schema.json}"
TIMEOUT="${AGY_TIMEOUT:-15m}"

if [ $# -lt 1 ] || [ -z "${1:-}" ]; then
  echo "usage: $(basename "$0") \"<prompt>\"" >&2
  exit 2
fi

if [ ! -f "$SCHEMA" ]; then
  echo "agy-task: schema not found: $SCHEMA" >&2
  exit 2
fi

OUT="$(agy -p "$1" \
        --model "$MODEL" \
        --output-format json \
        --json-schema "$SCHEMA" \
        --print-timeout "$TIMEOUT" \
        --dangerously-skip-permissions 2>&1)"
RC=$?

if [ "${AGY_RAW:-0}" = "1" ]; then
  printf '%s\n' "$OUT"
  exit $RC
fi

printf '%s' "$OUT" | python -c "
import sys, json

raw = sys.stdin.read()
try:
    d = json.loads(raw)
except Exception:
    sys.stderr.write('agy-task: agy did not return JSON. Raw output:\n')
    sys.stderr.write(raw[:4000] + '\n')
    sys.exit(1)

u = d.get('usage') or {}
print('agy[%s] %s | turns %s | %s tok | %.1fs' % (
    '${MODEL}', d.get('status'), d.get('num_turns'),
    u.get('total_tokens'), d.get('duration_seconds') or 0.0))
print('-' * 60)

so = d.get('structured_output')
if not so:
    print((d.get('response') or '').strip() or '(no response)')
    sys.exit(0 if d.get('status') == 'SUCCESS' else 1)

print('result      : %s' % so.get('status', '?'))
print('summary     : %s' % so.get('summary', ''))

files = so.get('files_changed') or []
if files:
    print('files       :')
    for f in files:
        print('              %s' % f)
else:
    print('files       : (none)')

print('verification: %s' % so.get('verification', ''))

issues = so.get('issues') or []
if issues:
    print('issues      :')
    for i in issues:
        print('              - %s' % i)
else:
    print('issues      : (none)')

sys.exit(0 if so.get('status') == 'complete' and d.get('status') == 'SUCCESS' else 1)
"
