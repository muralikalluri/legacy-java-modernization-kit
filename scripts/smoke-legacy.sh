#!/usr/bin/env bash
# Smoke test for the legacy app. Usage: scripts/smoke-legacy.sh [base-url]   (default http://localhost:8501)
# Mutates data (creates customers, claims, policies, a user). Run scripts/reset-db.sh before each run for exact counts
# (a second run without a reset still passes: the user-count checks below read the live total).
set -uo pipefail
BASE="${1:-http://localhost:8501}"
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

for u in admin adjuster1 supervisor1 csr1 auditor1 underwriter1; do login "$u"; done

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

# ---------------------------------------------------------------- M2: policies
check "policies list for any role" "200" "$(code -b "$JAR_DIR/csr1" "$BASE/policies/list.do")"
check "policies page at 10" "10" "$(curl -s -b "$JAR_DIR/csr1" "$BASE/policies/list.do" | grep -c 'data-policy-id')"
check "policy status filter" "0" "$(curl -s -b "$JAR_DIR/csr1" "$BASE/policies/search.do?status=CANCELLED" | grep 'data-policy-id' | grep -vc 'data-status="CANCELLED"')"
check "policy search is parameterised (apostrophe is fine)" "200" "$(code -b "$JAR_DIR/csr1" -G --data-urlencode "q=O'Brien" "$BASE/policies/search.do")"
check "policy search is not injectable" "0" "$(curl -s -b "$JAR_DIR/csr1" -G --data-urlencode "q=zzz%') OR 1=1 OR 'a' LIKE lower('%" "$BASE/policies/search.do" | grep -c 'data-policy-id')"
check "policy view" "200" "$(code -b "$JAR_DIR/csr1" "$BASE/policy/view.do?id=1")"
contains "policy view lists its claims" 'id="policy-claims"' "$(curl -s -b "$JAR_DIR/csr1" "$BASE/policy/view.do?id=1")"
check "unknown policy is 404" "404" "$(code -b "$JAR_DIR/csr1" "$BASE/policy/view.do?id=99999")"
check "csr may not edit policies" "403" "$(code -b "$JAR_DIR/csr1" "$BASE/policy/edit.do")"
check "underwriter may edit policies" "200" "$(code -b "$JAR_DIR/underwriter1" "$BASE/policy/edit.do")"
check "csr may not cancel" "403" "$(code -b "$JAR_DIR/csr1" -d 'id=1' "$BASE/policy/cancel.do")"
check "adjuster may not renew" "403" "$(code -b "$JAR_DIR/adjuster1" -d 'id=1&expectedEnd=2026-01-01' "$BASE/policy/renew.do")"
contains "policy with open claims cannot be cancelled" "open claim" "$(curl -s -b "$JAR_DIR/underwriter1" -d 'id=1' "$BASE/policy/cancel.do")"
contains "invalid policy form shows errors" 'id="errors"' "$(curl -s -b "$JAR_DIR/underwriter1" -d 'customerId=1&productType=BOAT&coverageLimit=x&premium=1&startDate=a&endDate=b' "$BASE/policy/save.do")"
contains "unknown customer rejected" "Customer not found" "$(curl -s -b "$JAR_DIR/underwriter1" -d 'customerId=99999&productType=AUTO&coverageLimit=1000&premium=10&startDate=2025-02-01&endDate=2026-01-31' "$BASE/policy/save.do")"
contains "end before start rejected" "End date must not be before" "$(curl -s -b "$JAR_DIR/underwriter1" -d 'customerId=1&productType=AUTO&coverageLimit=1000&premium=10&startDate=2026-02-01&endDate=2026-01-31' "$BASE/policy/save.do")"
loc="$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR_DIR/underwriter1" -d 'customerId=1&productType=AUTO&coverageLimit=1000&premium=10.50&startDate=2025-02-01&endDate=2026-01-31' "$BASE/policy/save.do")"
contains "new policy created" "/policy/view.do?id=" "$loc"
pid="${loc##*id=}"
contains "new policy gets a POL number" "POL-0000" "$(curl -s -b "$JAR_DIR/underwriter1" "$BASE/policy/view.do?id=$pid")"
contains "editing dates is refused" "Policy dates change only through renewal" "$(curl -s -b "$JAR_DIR/underwriter1" -d "id=$pid&customerId=1&productType=HOME&coverageLimit=2000&premium=10&startDate=2025-02-02&endDate=2026-01-31" "$BASE/policy/save.do")"
contains "changing the customer is refused" "customer of an existing policy" "$(curl -s -b "$JAR_DIR/underwriter1" -d "id=$pid&customerId=2&productType=HOME&coverageLimit=2000&premium=10&startDate=2025-02-01&endDate=2026-01-31" "$BASE/policy/save.do")"
curl -s -o /dev/null -b "$JAR_DIR/underwriter1" -d "id=$pid&customerId=1&productType=HOME&coverageLimit=2000&premium=10&startDate=2025-02-01&endDate=2026-01-31" "$BASE/policy/save.do"
contains "editing terms works" "HOME" "$(curl -s -b "$JAR_DIR/underwriter1" "$BASE/policy/view.do?id=$pid")"
curl -s -o /dev/null -b "$JAR_DIR/underwriter1" -d "id=$pid&expectedEnd=2026-01-31" "$BASE/policy/renew.do"
contains "renewal extends by one year from the old end" "2027-01-31" "$(curl -s -b "$JAR_DIR/underwriter1" "$BASE/policy/view.do?id=$pid")"
contains "double renew gets the stale message" "Policy status has changed" "$(curl -s -b "$JAR_DIR/underwriter1" -d "id=$pid&expectedEnd=2026-01-31" "$BASE/policy/renew.do")"
curl -s -o /dev/null -b "$JAR_DIR/supervisor1" -d "id=$pid" "$BASE/policy/cancel.do"
check "supervisor cancelled a policy without open claims" "1" "$(curl -s -b "$JAR_DIR/csr1" "$BASE/policy/view.do?id=$pid" | grep -c 'data-status="CANCELLED"')"
contains "double cancel gets a defined error" "Only active policies can be cancelled" "$(curl -s -b "$JAR_DIR/supervisor1" -d "id=$pid" "$BASE/policy/cancel.do")"
contains "cancelled policy cannot be renewed" "cancelled policy cannot be renewed" "$(curl -s -b "$JAR_DIR/underwriter1" -d "id=$pid&expectedEnd=2027-01-31" "$BASE/policy/renew.do")"
contains "cancelled policy cannot be edited" "Only active policies can be edited" "$(curl -s -b "$JAR_DIR/underwriter1" -d "id=$pid&customerId=1&productType=HOME&coverageLimit=2000&premium=10&startDate=2025-02-01&endDate=2027-01-31" "$BASE/policy/save.do")"

# claim page extras
page="$(curl -s -b "$JAR_DIR/auditor1" "$BASE/claim/view.do?id=$id")"
contains "claim page shows SLA" 'id="sla"' "$page"
contains "claim page shows reserve" 'id="reserve"' "$page"
contains "paid claim shows a letter preview" 'id="letter"' "$page"

# Every page must render to the end. A JSP that fails half way still answers 200 (the status is already sent),
# so check for the closing tag. This caught /policy/view.do failing for roles that can renew and cancel.
for role in admin underwriter1 supervisor1; do
  for url in "/policy/view.do?id=1" "/policy/view.do?id=$pid" "/customers/view.do?id=1" "/claim/view.do?id=$id" "/policies/list.do" "/customers/list.do" "/claims/search.do"; do
    contains "page renders to the end: $role $url" "</html>" "$(curl -s -b "$JAR_DIR/$role" "$BASE$url")"
  done
done
contains "underwriter sees the renew and cancel buttons" "Cancel policy" "$(curl -s -b "$JAR_DIR/underwriter1" "$BASE/policy/view.do?id=1")"

# ---------------------------------------------------------------- M2: reports
for r in index claimsByStatus claimsByStatusPdf policyExpiry claimsByAdjuster; do
  check "csr may not open report $r" "403" "$(code -b "$JAR_DIR/csr1" "$BASE/reports/$r.do")"
  check "auditor may open report $r" "200" "$(code -b "$JAR_DIR/auditor1" "$BASE/reports/$r.do")"
done
check "status report always lists all 6 statuses" "6" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/reports/claimsByStatus.do" | grep -c 'data-status=')"
check "pdf content type" "application/pdf" "$(curl -s -o /dev/null -w '%{content_type}' -b "$JAR_DIR/auditor1" "$BASE/reports/claimsByStatusPdf.do")"
check "pdf starts with %PDF" "%PDF" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/reports/claimsByStatusPdf.do" | head -c 4)"
expiry="$(curl -s -b "$JAR_DIR/auditor1" "$BASE/reports/policyExpiry.do?asOf=2026-01-01&days=90")"
ends="$(grep -o '<td>[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}</td>' <<<"$expiry" | grep -o '[0-9-]\{10\}')"
check "expiry report has rows" "yes" "$([ -n "$ends" ] && echo yes || echo no)"
check "expiry report is ordered by end date" "$ends" "$(sort <<<"$ends")"
check "expiry report stays inside the inclusive window" "0" "$(awk '$1 < "2026-01-01" || $1 > "2026-04-01"' <<<"$ends" | wc -l | tr -d ' ')"
contains "expiry report rejects a bad window" 'id="errors"' "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/reports/policyExpiry.do?days=9999")"
contains "adjuster report has an Unassigned bucket" "Unassigned" "$(curl -s -b "$JAR_DIR/auditor1" "$BASE/reports/claimsByAdjuster.do")"

# ---------------------------------------------------------------- M2: admin
for a in index users userEdit jobs; do
  check "csr may not open admin $a" "403" "$(code -b "$JAR_DIR/csr1" "$BASE/admin/$a.do")"
  check "admin may open admin $a" "200" "$(code -b "$JAR_DIR/admin" "$BASE/admin/$a.do")"
done
check "user search finds a user" "1" "$(curl -s -b "$JAR_DIR/admin" "$BASE/admin/users.do?q=auditor1" | grep -c 'data-user-id')"
users_total="$(curl -s -b "$JAR_DIR/admin" "$BASE/admin/users.do" | grep -o 'id="total">[0-9]*' | grep -o '[0-9]*$')"
users_expected="$(( users_total > 10 ? 10 : users_total ))"
check "SEEDED FLAW user search injectable (#3)" "$users_expected" "$(curl -s -b "$JAR_DIR/admin" -G --data-urlencode "q=zzz%') OR 1=1 OR 'a' LIKE lower('%" "$BASE/admin/users.do" | grep -c 'data-user-id')"
check "a search with no match finds no users" "0" "$(curl -s -b "$JAR_DIR/admin" "$BASE/admin/users.do?q=zzzznomatch" | grep -c 'data-user-id')"
contains "admin cannot deactivate self" "cannot deactivate your own account" "$(curl -s -b "$JAR_DIR/admin" "$BASE/admin/userToggle.do?id=1")"
# SEEDED FLAW: userSave has no roles=, so a CSR can create an ADMIN account (use a throwaway user, never a seeded one)
tu="smoke$stamp"
check "SEEDED FLAW csr can create an admin via userSave" "302" "$(code -b "$JAR_DIR/csr1" -d "username=$tu&password=$tu-demo&displayName=Smoke+Admin&role=ADMIN&active=true" "$BASE/admin/userSave.do")"
curl -s -o /dev/null -c "$JAR_DIR/$tu" -d "username=$tu&password=$tu-demo" "$BASE/loginSubmit.do"
check "the throwaway admin can open admin pages" "200" "$(code -b "$JAR_DIR/$tu" "$BASE/admin/users.do")"
# Failures forward to an admin-only page, so a non-admin caller gets 403 instead of the message (legacy quirk, see ADR).
check "csr userSave failure ends in 403 (forward re-checks roles)" "403" "$(code -b "$JAR_DIR/csr1" -d "username=$tu&password=x&displayName=Dup&role=CSR&active=true" "$BASE/admin/userSave.do")"
contains "duplicate username rejected" "already in use" "$(curl -s -b "$JAR_DIR/admin" -d "username=$tu&password=x&displayName=Dup&role=CSR&active=true" "$BASE/admin/userSave.do")"
contains "unknown role rejected" "Unknown role" "$(curl -s -b "$JAR_DIR/admin" -d "username=${tu}b&password=x&displayName=Dup&role=KING&active=true" "$BASE/admin/userSave.do")"
tid="$(curl -s -b "$JAR_DIR/admin" "$BASE/admin/users.do?q=$tu" | grep -o 'data-user-id="[0-9]*"' | head -1 | grep -o '[0-9]*')"
curl -s -o /dev/null -b "$JAR_DIR/admin" "$BASE/admin/userToggle.do?id=$tid"
check "toggled user can no longer log in" "1" "$(curl -s -d "username=$tu&password=$tu-demo" "$BASE/loginSubmit.do" | grep -c 'Invalid username or password')"

# ---------------------------------------------------------------- M2: jobs
# asOf in 2000 lapses nothing, so the seeded policies stay ACTIVE. SEEDED FLAW: csr1 may trigger the job.
check "csr cannot open the jobs page" "403" "$(code -b "$JAR_DIR/csr1" "$BASE/admin/jobs.do")"
loc="$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR_DIR/csr1" -d 'asOf=2000-01-01' "$BASE/admin/jobRun.do")"
contains "SEEDED FLAW csr can trigger the job" "/admin/jobs.do?run=" "$loc"
curl -s -o /dev/null -b "$JAR_DIR/csr1" -d 'asOf=2000-01-01' "$BASE/admin/jobRun.do"
jobs="$(curl -s -b "$JAR_DIR/admin" "$BASE/admin/jobs.do")"
check "jobs page lists the runs" "2" "$(grep -c 'data-run-id' <<<"$jobs" | awk '{print ($1>=2)?2:$1}')"
check "an as-of date with nothing to lapse updates 0 rows" "0" "$(grep -o 'class="rows">[0-9]*' <<<"$jobs" | head -1 | grep -o '[0-9]*$')"
contains "bad as-of date is rejected" 'id="errors"' "$(curl -s -b "$JAR_DIR/admin" -d 'asOf=nope' "$BASE/admin/jobRun.do")"
contains "scheduler is off by default" ">off<" "$jobs"

curl -s -o /dev/null -b "$JAR_DIR/admin" "$BASE/logout.do"
echo; echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
