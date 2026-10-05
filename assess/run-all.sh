#!/usr/bin/env bash
# Regenerates every assessment output under assess/results/.
#   ./assess/run-all.sh             default: OSV cache first, query only cache misses; run the OpenRewrite dry run
#   ./assess/run-all.sh --offline   no network: OSV from the committed cache, OpenRewrite section reused as committed
#   ./assess/run-all.sh --refresh   re-query OSV for every library and rewrite the cache
# Stages (M3): inventory -> deps -> jakarta. M4 adds structure -> security -> estimate -> report.
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

if [ ! -f legacy-app/target/portal.war ]; then
  echo "== build legacy-app WAR (Java 8 container, as in CI)"
  docker run --rm -v "$PWD/legacy-app":/src -w /src maven:3.9-eclipse-temurin-8 mvn -B -q -DskipTests package
fi

ASSESS="java -jar assess/target/assess.jar"

echo "== inventory"
$ASSESS inventory

echo "== deps ${MODE}"
$ASSESS deps ${MODE}

echo "== jakarta"
if [ "$MODE" = "--offline" ]; then
  $ASSESS jakarta --rewrite reuse
else
  $ASSESS jakarta --rewrite run
fi

echo "done. outputs in assess/results/"
