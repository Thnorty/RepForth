#!/usr/bin/env bash
# codex-task.sh — run `codex` headless as a subagent and print ONLY its final report.
#
# The sibling of agy-task.sh, and deliberately the same shape: a one-line
# telemetry header plus the five fields of subagent-report.schema.json. Both
# wrappers answer in the same format so a task can be handed to either one and
# the reports compared without translating between them.
#
# Everything in between — every command it runs, every file it reads, its own
# reasoning — stays in the child process. A single audit produced 776 lines of
# stdout while this printed nine.
#
# Usage:
#   tools/codex-task.sh "<prompt>"
#
# Env overrides:
#   CODEX_MODEL    model id             (default: gpt-5.6-sol)
#   CODEX_EFFORT   reasoning effort     (default: high)
#   CODEX_SANDBOX  read-only | workspace-write | danger-full-access
#                                       (default: workspace-write)
#   CODEX_SCHEMA   path to JSON schema  (default: tools/subagent-report.schema.json)
#   CODEX_TIMEOUT  wall-clock limit     (default: 15m)
#   CODEX_RAW      set to 1 to dump the raw transcript instead of the report

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODEL="${CODEX_MODEL:-gpt-5.6-sol}"
EFFORT="${CODEX_EFFORT:-high}"
SANDBOX="${CODEX_SANDBOX:-workspace-write}"
SCHEMA="${CODEX_SCHEMA:-$HERE/subagent-report.schema.json}"
TIMEOUT="${CODEX_TIMEOUT:-15m}"

if [ $# -lt 1 ] || [ -z "${1:-}" ]; then
  echo "usage: $(basename "$0") \"<prompt>\"" >&2
  exit 2
fi

if [ ! -f "$SCHEMA" ]; then
  echo "codex-task: schema not found: $SCHEMA" >&2
  exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
REPORT="$WORK/report.json"
LOG="$WORK/transcript.log"

# --ignore-user-config keeps ~/.codex/config.toml out of this: the MCP servers
# declared there (Unity, a node REPL, context7 with its API key in plain text)
# are irrelevant to a repo task, slow to start, and would put a credential in
# the subagent's environment for no reason. The cost is that the config's other
# settings go too, so the ones that matter are passed back explicitly below.
ARGS=(
  exec
  --ignore-user-config
  --ephemeral
  --skip-git-repo-check
  -m "$MODEL"
  -c "model_reasoning_effort=\"$EFFORT\""
  -s "$SANDBOX"
  --output-schema "$SCHEMA"
  -o "$REPORT"
)

# On Windows the default sandbox rejects `powershell -Command` and `cmd /c` as
# unvetted shell wrappers, which is every command codex tries to run. It does
# not fail loudly: the run exits 0 having read nothing and reports "Not started
# yet". The user's own config sets this; --ignore-user-config drops it, so it
# has to come back or the subagent is silently blind.
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) ARGS+=(-c 'windows.sandbox="elevated"') ;;
esac

STARTED=$(date +%s)
# stdin from /dev/null is load-bearing, not tidiness: `codex exec` reads stdin
# when it is attached and appends it to the prompt, so an inherited pipe that
# never closes hangs the run forever. Found by watching one sit for five
# minutes printing "Reading additional input from stdin...".
timeout "$TIMEOUT" codex "${ARGS[@]}" -- "$1" > "$LOG" 2>&1 < /dev/null
RC=$?
ELAPSED=$(( $(date +%s) - STARTED ))

if [ "${CODEX_RAW:-0}" = "1" ]; then
  cat "$LOG"
  exit $RC
fi

if [ $RC -eq 124 ]; then
  echo "codex-task: timed out after $TIMEOUT" >&2
  tail -c 2000 "$LOG" >&2
  exit 124
fi

MODEL="$MODEL" EFFORT="$EFFORT" ELAPSED="$ELAPSED" RC="$RC" \
REPORT="$REPORT" LOG="$LOG" python -c "
import json, os, re, sys

report_path, log_path = os.environ['REPORT'], os.environ['LOG']
log = open(log_path, encoding='utf-8', errors='replace').read()

try:
    with open(report_path, encoding='utf-8') as fh:
        so = json.load(fh)
except Exception:
    sys.stderr.write('codex-task: no structured report was written. Tail of transcript:\n')
    sys.stderr.write(log[-4000:] + '\n')
    sys.exit(1)

# 'tokens used' and its figure land on separate lines in this output.
m = re.search(r'tokens used\s*\n?\s*([\d,]+)', log)
tokens = m.group(1) if m else '?'
commands = len(re.findall(r'^exec\b', log, re.M))

print('codex[%s %s] %s | %s cmds | %s tok | %ss' % (
    os.environ['MODEL'], os.environ['EFFORT'],
    so.get('status', '?'), commands, tokens, os.environ['ELAPSED']))
print('-' * 60)

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

# The process exit code is not a success signal: a run whose every command was
# rejected by policy still exited 0. The report's own status is the only thing
# that distinguishes work done from work prevented.
blocked = log.count('blocked by policy')
if blocked:
    print()
    print('WARNING     : %d command(s) rejected by sandbox policy — the subagent '
          'may have been unable to read anything.' % blocked)

sys.exit(0 if so.get('status') == 'complete' and int(os.environ['RC']) == 0 else 1)
"
