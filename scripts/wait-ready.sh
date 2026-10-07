#!/usr/bin/env bash
# Waits until the app really works, not just until Tomcat listens: the login page answers AND a signed-in search for the
# seeded surname "almeida" finds the three seeded customers (so the webapp, the datasource and the seed are all fine).
# Usage: scripts/wait-ready.sh [base-url] [timeout-seconds]
set -uo pipefail
BASE="${1:-http://localhost:8501}"
TIMEOUT="${2:-120}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
end=$((SECONDS + TIMEOUT))
while [ "$SECONDS" -lt "$end" ]; do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/login.do")" = "200" ]; then
    curl -s -o /dev/null -c "$JAR" -d 'username=auditor1&password=auditor1-demo' "$BASE/loginSubmit.do"
    total="$(curl -s -b "$JAR" "$BASE/customers/search.do?q=almeida" | grep -o 'id="total">[0-9]*' | grep -o '[0-9]*$')"
    if [ "${total:-}" = "3" ]; then
      echo "ready: $BASE"
      exit 0
    fi
  fi
  sleep 2
done
echo "not ready after ${TIMEOUT}s: $BASE" >&2
exit 1
