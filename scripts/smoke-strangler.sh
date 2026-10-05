#!/usr/bin/env bash
# Checks the things only the whole stack can show: routing, cookies, redirects, spoofed headers and logout, through the gateway.
# Usage: scripts/smoke-strangler.sh [gateway-url]   (default http://localhost:8080). Creates one customer; run scripts/reset-db.sh after.
set -uo pipefail
BASE="${1:-http://localhost:8080}"
DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
pass=0; fail=0
ok()   { pass=$((pass+1)); echo "PASS $1"; }
bad()  { fail=$((fail+1)); echo "FAIL $1 ($2)"; }
check() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "expected '$2', got '$3'"; fi; }
has()   { if grep -q -- "$2" <<<"$3"; then ok "$1"; else bad "$1" "missing '$2'"; fi; }
hasnt() { if grep -q -- "$2" <<<"$3"; then bad "$1" "found '$2'"; else ok "$1"; fi; }

login() { curl -s -o /dev/null -D "$DIR/$1.headers" -c "$DIR/$1.jar" -d "username=$1&password=$1-demo" "$BASE/loginSubmit.do"; }
login csr1; login admin

# ---- the bridge cookie
cookie="$(grep -i '^Set-Cookie: HARBOR_AUTH=' "$DIR/csr1.headers" | tr -d '\r')"
has "legacy login sets the bridge cookie" "HARBOR_AUTH=v1\." "$cookie"
has "cookie is HttpOnly" "HttpOnly" "$cookie"
has "cookie is SameSite=Lax" "SameSite=Lax" "$cookie"
has "cookie has Path=/" "Path=/" "$cookie"
hasnt "cookie has no Domain" "Domain" "$cookie"
hasnt "cookie is not Secure over http" "Secure" "$cookie"
keys="$(python3 - "$cookie" <<'PY'
import base64, json, re, sys
m = re.search(r"HARBOR_AUTH=v1\.([^.;]+)\.", sys.argv[1])
raw = m.group(1)
payload = json.loads(base64.urlsafe_b64decode(raw + "=" * (-len(raw) % 4)))
print(",".join(sorted(payload)))
PY
)"
check "the cookie names who, which login and when, and carries no role" "auth,exp,iat,sid,u" "$keys"

# ---- who serves what
page() { curl -s -b "$DIR/$1.jar" "$BASE$2"; }
has   "customers are served by the modern app" "migrated modules" "$(page csr1 /customers/list.do)"
has   "claim search is served by the modern app" "migrated modules" "$(page csr1 '/claims/search.do?q=CLM-000007')"
has   "policies are still served by the legacy app" "Deliberately insecure" "$(page csr1 /policies/list.do)"
has   "claim pages are still served by the legacy app" "Deliberately insecure" "$(page csr1 '/claim/view.do?id=1')"
hasnt "claim search is not a prefix route: /claims/search.do/x is legacy" "migrated modules" "$(page csr1 /claims/search.do/x)"
hasnt "/customers without the slash is not a migrated path" "migrated modules" "$(page csr1 /customers)"

# ---- the modern app never sets session or csrf cookies
allheaders="$( (for url in /customers/list.do /customers/view.do?id=1 /customers/edit.do '/claims/search.do?q=a' /customers/nothing.do; do curl -s -D - -o /dev/null -b "$DIR/csr1.jar" "$BASE$url"; done; curl -s -D - -o /dev/null "$BASE/customers/list.do") | tr -d '\r')"
if grep -qiE '^Set-Cookie: *(JSESSIONID|HARBOR_MODERN_SID|XSRF-TOKEN)' <<<"$allheaders"; then bad "modern responses never set a session or csrf cookie" "found one"; else ok "modern responses never set a session or csrf cookie"; fi

# ---- redirects are relative, and spoofed forwarded headers change nothing
anon="$(curl -s -D - -o /dev/null "$BASE/customers/list.do" | tr -d '\r')"
has "anonymous modern request redirects to the login page" "^Location: /login.do$" "$anon"
spoof="$(curl -s -D - -o /dev/null -H 'X-Forwarded-Host: evil.example' -H 'X-Forwarded-Proto: https' -H 'X-Forwarded-Port: 9999' -H 'Forwarded: host=evil.example;proto=https' "$BASE/customers/list.do" | tr -d '\r')"
has   "spoofed forwarded headers leave the login redirect relative" "^Location: /login.do$" "$spoof"
hasnt "no evil host in any header" "evil.example" "$spoof"

edit="$(curl -s -b "$DIR/csr1.jar" -H 'X-Forwarded-Host: evil.example' -H 'X-Forwarded-Proto: https' "$BASE/customers/edit.do")"
token="$(sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p' <<<"$edit" | head -1)"
[ -n "$token" ] && ok "the edit form carries a csrf token" || bad "the edit form carries a csrf token" "none found"
stamp="$(date +%s)"
created="$(curl -s -D - -o /dev/null -b "$DIR/csr1.jar" -H 'X-Forwarded-Host: evil.example' -H 'X-Forwarded-Proto: https' --data-urlencode "_csrf=$token" -d "id=&firstName=Smoke&lastName=Strangler$stamp&email=smoke$stamp@example.test" "$BASE/customers/save.do" | tr -d '\r')"
has   "creating a customer redirects with a relative Location" "^Location: /customers/view.do?id=[0-9]*$" "$created"
hasnt "the redirect does not follow a spoofed host" "evil.example" "$created"
refresh="$(curl -s -D - -o /dev/null -b "$DIR/csr1.jar" -H 'X-Forwarded-Proto: https' "$BASE/customers/list.do" | tr -d '\r')"
hasnt "a spoofed X-Forwarded-Proto does not make cookies Secure" "Secure" "$refresh"
noToken="$(curl -s -o /dev/null -w '%{http_code}' -b "$DIR/csr1.jar" -d "firstName=x&lastName=y&email=z@z.zz" "$BASE/customers/save.do")"
check "a POST without the csrf token is refused" "403" "$noToken"
check "a GET delete is refused for an administrator" "405" "$(curl -s -o /dev/null -w '%{http_code}' -b "$DIR/admin.jar" "$BASE/customers/delete.do?id=1")"

# ---- path tricks must never reach data without a login
for url in '//customers/list.do' '/customers/%2e%2e/policies/list.do' '/policies/%2e%2e/customers/list.do' '/customers/list.do;jsessionid=x' '/customers/./list.do' '/CUSTOMERS/list.do' '/customers/list.do/'; do
  out="$(curl -s -g -w '\n%{http_code}' --path-as-is "$BASE$url")"
  code="$(tail -1 <<<"$out")"
  if [ "$code" = "200" ] && grep -q 'data-customer-id' <<<"$out"; then bad "no data for an anonymous caller: $url" "got customers"; else ok "no data for an anonymous caller: $url (HTTP $code)"; fi
done

# ---- logout revokes exactly that login, in the modern app too
login csr1; cp "$DIR/csr1.jar" "$DIR/csrA.jar"; login csr1; cp "$DIR/csr1.jar" "$DIR/csrB.jar"
check "login A works on a migrated page" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b "$DIR/csrA.jar" "$BASE/customers/list.do")"
check "login B works on a migrated page" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b "$DIR/csrB.jar" "$BASE/customers/list.do")"
curl -s -o /dev/null -D "$DIR/logout.headers" -b "$DIR/csrA.jar" "$BASE/logout.do"
has   "logout clears the bridge cookie" "HARBOR_AUTH=; Path=/; Max-Age=0" "$(tr -d '\r' < "$DIR/logout.headers")"
check "after logging out, login A is refused on a migrated page" "302" "$(curl -s -o /dev/null -w '%{http_code}' -b "$DIR/csrA.jar" "$BASE/customers/list.do")"
check "a stolen copy of A's cookie is refused too (the server remembers the logout)" "302" "$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: $(grep -o 'HARBOR_AUTH.*' "$DIR/csrA.jar" | awk '{print $1"="$2}' | head -1)" "$BASE/customers/list.do")"
check "login B of the same user is untouched" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b "$DIR/csrB.jar" "$BASE/customers/list.do")"

echo; echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
