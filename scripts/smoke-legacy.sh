#!/usr/bin/env bash
# Smoke test for the legacy app. Usage: scripts/smoke-legacy.sh [base-url]   (default http://localhost:8081)
# Mutates data (creates a customer and a claim). Run scripts/reset-db.sh afterwards if you need a clean seed.
set -uo pipefail
BASE="${1:-http://localhost:8081}"
JAR_DIR="$(mktemp -d)"
trap 'rm -rf "$JAR_DIR"' EXIT
pass=0; fail=0

check() { # name, expected, actual
  if [ "$2" = "$3" ]; then pass=$((pass+1)); echo "PASS $1"; else fail=$((fail+1)); echo "FAIL $1 (expected '$2', got '$3')"; fi
}
contains() { # name, needle, haystack
  if grep -q -- "$2" <<<"$3"; then pass=$((pass+1)); echo "PASS $1"; else fail=$((fail+1)); echo "FAIL $1 (missing '$2')"; fi
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }
login() { curl -s -o /dev/null -c "$JAR_DIR/$1" -d "username=$1&password=$1-demo" "$BASE/loginSubmit.do"; }

check "no session redirects to login" "302" "$(code "$BASE/customers/list.do")"
check "login page is public" "200" "$(code "$BASE/login.do")"
check "bad password stays on login with error" "1" "$(curl -s -d 'username=admin&password=nope' "$BASE/loginSubmit.do" | grep -c 'Invalid username or password')"
check "inactive user cannot log in" "1" "$(curl -s -d 'username=retired1&password=retired1-demo' "$BASE/loginSubmit.do" | grep -c 'Invalid username or password')"

for u in admin adjuster1 supervisor1 csr1 auditor1; do login "$u"; done

# Customers
page="$(curl -s -b "$JAR_DIR/csr1" "$BASE/customers/search.do?q=almeida")"
contains "customer search finds seeded name" "Almeida" "$page"
contains "customer search shows total hook" 'id="total"' "$page"
check "customer search is case-insensitive" "$(printf '%s' "$page" | grep -c 'data-customer-id')" "$(curl -s -b "$JAR_DIR/csr1" "$BASE/customers/search.do?q=ALMEIDA" | grep -c 'data-customer-id')"
check "customer list pages at 10" "10" "$(curl -s -b "$JAR_DIR/csr1" "$BASE/customers/list.do" | grep -c 'data-customer-id')"
check "page past the end is empty 200" "0:200" "$(curl -s -b "$JAR_DIR/csr1" "$BASE/customers/list.do?page=99" | grep -c 'data-customer-id'):$(code -b "$JAR_DIR/csr1" "$BASE/customers/list.do?page=99")"
check "customer view" "200" "$(code -b "$JAR_DIR/csr1" "$BASE/customers/view.do?id=1")"
check "unknown customer is 404" "404" "$(code -b "$JAR_DIR/csr1" "$BASE/customers/view.do?id=99999")"
check "auditor may not open edit form" "403" "$(code -b "$JAR_DIR/auditor1" "$BASE/customers/edit.do")"
check "csr may open edit form" "200" "$(code -b "$JAR_DIR/csr1" "$BASE/customers/edit.do")"
check "csr may not delete" "403" "$(code -b "$JAR_DIR/csr1" "$BASE/customers/delete.do?id=1")"
contains "customer with policies cannot be deleted" "cannot be deleted" "$(curl -s -b "$JAR_DIR/admin" "$BASE/customers/delete.do?id=1")"
contains "invalid customer shows errors hook" 'id="errors"' "$(curl -s -b "$JAR_DIR/csr1" -d 'firstName=&lastName=X&email=bad' "$BASE/customers/save.do")"
stamp="$(date +%s)"
loc="$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR_DIR/csr1" -d "firstName=Smoke&lastName=Test$stamp&email=smoke$stamp@example.test" "$BASE/customers/save.do")"
contains "new customer redirects to view" "/customers/view.do?id=" "$loc"
contains "duplicate email rejected" "already in use" "$(curl -s -b "$JAR_DIR/csr1" -d "firstName=Smoke&lastName=Dup&email=smoke$stamp@example.test" "$BASE/customers/save.do")"

# Seeded SQL injection #1 (customer search)
all="$(curl -s -b "$JAR_DIR/csr1" -G --data-urlencode "q=zzz%') OR 1=1 OR 'a' LIKE lower('%" "$BASE/customers/search.do" | grep -c 'data-customer-id')"
check "SEEDED FLAW customer search injectable" "10" "$all"
check "apostrophe breaks legacy search with generic 500" "500" "$(code -b "$JAR_DIR/csr1" -G --data-urlencode "q=O'Brien" "$BASE/customers/search.do")"

# Claims
check "claims list pages at 10" "10" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/claims/search.do" | grep -c 'data-claim-id')"
check "status filter returns only that status" "0" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/claims/search.do?status=PAID" | grep 'data-claim-id' | grep -vc 'data-status="PAID"')"
check "unknown status returns no rows" "0" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/claims/search.do?status=NOPE" | grep -c 'data-claim-id')"
check "claim lookup by number" "1" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/claims/search.do?q=CLM-000007" | grep -c 'data-claim-id')"
all="$(curl -s -b "$JAR_DIR/auditor1" -G --data-urlencode "q=zzz%') OR 1=1 OR 'a' LIKE lower('%" "$BASE/claims/search.do" | grep -c 'data-claim-id')"
check "SEEDED FLAW claim search injectable" "10" "$all"
check "unknown claim is 404" "404" "$(code -b "$JAR_DIR/auditor1" "$BASE/claim/view.do?id=99999")"
check "auditor may not file a claim" "403" "$(code -b "$JAR_DIR/auditor1" "$BASE/claim/new.do")"
check "csr may not transition" "403" "$(code -b "$JAR_DIR/csr1" -d 'id=1&toStatus=UNDER_REVIEW' "$BASE/claim/transition.do")"

# Create a claim: policy 1 is ACTIVE (limit 50000). Dates are inside its period.
pol="POL-000001"
start="$(curl -s -b "$JAR_DIR/admin" "$BASE/customers/view.do?id=1" | grep -o '[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\} to' | head -1 | cut -c1-10)"
contains "claim above limit rejected" "exceeds the policy coverage limit" "$(curl -s -b "$JAR_DIR/adjuster1" -d "policyNumber=$pol&lossDate=$start&amountClaimed=9999999&description=x" "$BASE/claim/save.do")"
contains "loss date before policy start rejected" "outside the policy period" "$(curl -s -b "$JAR_DIR/adjuster1" -d "policyNumber=$pol&lossDate=2000-01-01&amountClaimed=100&description=x" "$BASE/claim/save.do")"
contains "unknown policy rejected" "Policy not found" "$(curl -s -b "$JAR_DIR/adjuster1" -d "policyNumber=POL-999999&lossDate=$start&amountClaimed=100&description=x" "$BASE/claim/save.do")"
loc="$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR_DIR/adjuster1" -d "policyNumber=$pol&lossDate=$start&amountClaimed=12000.00&description=Smoke+claim" "$BASE/claim/save.do")"
contains "claim created and redirected" "/claim/view.do?id=" "$loc"
id="${loc##*id=}"
check "new claim is NEW" "1" "$(curl -s -b "$JAR_DIR/adjuster1" "$BASE/claim/view.do?id=$id" | grep -c 'data-status="NEW"')"
contains "cannot skip review" "cannot move from NEW to APPROVED" "$(curl -s -b "$JAR_DIR/supervisor1" -d "id=$id&toStatus=APPROVED" "$BASE/claim/transition.do")"
curl -s -o /dev/null -b "$JAR_DIR/adjuster1" -d "id=$id&toStatus=UNDER_REVIEW" "$BASE/claim/transition.do"
contains "adjuster cannot approve above threshold" "may not move this claim to APPROVED" "$(curl -s -b "$JAR_DIR/adjuster1" -d "id=$id&toStatus=APPROVED" "$BASE/claim/transition.do")"
contains "deny needs a reason" "reason is required" "$(curl -s -b "$JAR_DIR/adjuster1" -d "id=$id&toStatus=DENIED" "$BASE/claim/transition.do")"
curl -s -o /dev/null -b "$JAR_DIR/supervisor1" -d "id=$id&toStatus=APPROVED" "$BASE/claim/transition.do"
check "supervisor approved above threshold" "1" "$(curl -s -b "$JAR_DIR/supervisor1" "$BASE/claim/view.do?id=$id" | grep -c 'data-status="APPROVED"')"
curl -s -o /dev/null -b "$JAR_DIR/supervisor1" -d "id=$id&toStatus=PAID" "$BASE/claim/transition.do"
contains "double PAID gets a defined error" "cannot move from PAID to PAID" "$(curl -s -b "$JAR_DIR/supervisor1" -d "id=$id&toStatus=PAID" "$BASE/claim/transition.do")"
contains "transitions leave notes" "Status changed from APPROVED to PAID" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/claim/view.do?id=$id")"

curl -s -o /dev/null -b "$JAR_DIR/admin" "$BASE/logout.do"
echo; echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
