#!/usr/bin/env bash
# Regenerates every assessment output: assess/results/*.json, sample-deliverable/QUICK_ASSESSMENT.md, sample-deliverable/ASSESSMENT_AND_ROADMAP.md
# and the results block in README.md. PDFs are separate: ./assess/make-pdf.sh.
#   ./assess/run-all.sh             OSV from the cache (queries only misses); OpenRewrite dry run runs
#   ./assess/run-all.sh --offline   no network: OSV from the committed cache, OpenRewrite result reused if the sources are unchanged
#   ./assess/run-all.sh --refresh   re-query OSV for every library and rewrite the cache
# Semgrep runs when it is installed; otherwise the committed result is reused if sources and rules are unchanged.
# Stages: inventory -> deps -> jakarta -> structure -> security -> estimate -> report
set -euo pipefail
cd "$(dirname "$0")/.."

MODE=""
for arg in "$@"; do
  case "$arg" in
    --offline|--refresh) MODE="$arg" ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

echo "== build assess"
mvn -B -q -f assess/pom.xml -DskipTests package

if [ ! -f legacy-app/target/portal.war ] || [ ! -d legacy-app/target/classes ]; then
  echo "== build legacy-app (Java 8 container, as in CI)"
  docker run --rm -v "$PWD/legacy-app":/src -w /src maven:3.9-eclipse-temurin-8 mvn -B -q -DskipTests package
fi

ASSESS="java -jar assess/target/assess.jar"

echo "== inventory";  $ASSESS inventory
echo "== deps ${MODE}"; $ASSESS deps ${MODE}
echo "== jakarta"
if [ "$MODE" = "--offline" ]; then $ASSESS jakarta --rewrite reuse; else $ASSESS jakarta --rewrite run; fi
echo "== structure";  $ASSESS structure
echo "== security"
if command -v semgrep >/dev/null 2>&1; then $ASSESS security --semgrep run; else $ASSESS security --semgrep reuse; fi
echo "== estimate";   $ASSESS estimate
echo "== report";     $ASSESS report

echo "done. outputs in assess/results/, sample-deliverable/ and the README results block"
