#!/usr/bin/env bash
# Truncates and reseeds the demo database. Run before each characterisation test suite.
set -euo pipefail
cd "$(dirname "$0")/.."
DB="${POSTGRES_DB:-harbor}"; USER_="${POSTGRES_USER:-harbor}"
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$USER_" -d "$DB" -q <<'SQL'
TRUNCATE claim_notes, claims, policies, customers, app_users, roles RESTART IDENTITY CASCADE;
SQL
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$USER_" -d "$DB" -q < legacy-app/db/V2__seed.sql > /dev/null
echo "database reset"
