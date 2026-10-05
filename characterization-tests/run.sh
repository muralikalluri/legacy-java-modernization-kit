#!/usr/bin/env bash
# Runs the characterisation suite.
#   characterization-tests/run.sh                      legacy at http://localhost:8081, DB reset first
#   characterization-tests/run.sh --twice              also run a second time WITHOUT a reset (proves no dependence on leftovers)
#   characterization-tests/run.sh --target modern --base-url http://localhost:8080 --no-reset
# The test order is random; the seed is printed so a failure can be replayed with SEED=<n>.
set -euo pipefail
cd "$(dirname "$0")/.."

TARGET="legacy"; BASE="http://localhost:8081"; RESET=1; TWICE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --target) TARGET="$2"; shift 2 ;;
    --base-url) BASE="$2"; shift 2 ;;
    --no-reset) RESET=0; shift ;;
    --twice) TWICE=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

run_suite() {
  local seed="${SEED:-$RANDOM}"
  echo "== characterisation suite: target=$TARGET base=$BASE order seed=$seed"
  mvn -B -f characterization-tests/pom.xml test -Dbase.url="$BASE" -Dtarget="$TARGET" -Dorder.seed="$seed"
}

# Wait first: the container can report healthy while its init scripts are still running, and a reset must not race them.
scripts/wait-ready.sh "$BASE"
if [ "$RESET" = "1" ]; then scripts/reset-db.sh; scripts/wait-ready.sh "$BASE"; fi
run_suite
if [ "$TWICE" = "1" ]; then
  echo "== second run without a reset"
  SEED="" run_suite
fi
