# ADR 0004: The characterisation contract (M5)

Status: accepted. The tests in `characterization-tests/` capture what the legacy app does for **Customers** and **Claims search**. In M6 the same tests run **unchanged** against the migrated routes through the gateway. If they stay green, the migration kept the behaviour; the divergences below are the only differences allowed, and they are a closed list.

> The seeded flaws in the legacy app are deliberate (see ADR 0001). The tests pin the behaviour users rely on; they do not endorse the flaws.

## How it runs

```bash
characterization-tests/run.sh --twice        # legacy: reset the database, run, then run again without a reset
characterization-tests/run.sh --target modern --base-url http://localhost:8080 --no-reset
characterization-tests/mutants/run-mutants.sh   # proves the tests can fail
```

Plain HTTP with Java's `HttpClient` (cookie jar, redirects never followed) and jsoup. Test order is random and the seed is printed, so a failure can be replayed with `SEED=<n>`.

## What the pages promise (hooks, messages, status codes)

Tests assert only on this contract, never on layout.

- **Hooks:** `#results`, `#total`, `#pager` with `#prev` and `#next`, `[data-customer-id]`, `[data-claim-id]`, `[data-status]`, `#errors li`, `#customer-name`, `#customer-details`, `#policies`, `#edit-link`, `#delete-link`, and the search forms (`input[name=q]`, `select[name=status]`). Row selectors are scoped to `#results` because other tables carry the same attributes. A page with zero rows must still be a results page (200, `#results`, `#total`): a dropped session also shows zero rows.
- **Form fields:** `id`, `firstName`, `lastName`, `email`, `phone`, `dateOfBirth`, `addressLine`, `city`, `state`, `postalCode`.
- **Messages** (the raw property name is part of the text): `firstName is required`, `firstName must be at most 50 characters`, `Email address is not valid`, `Phone number is not valid`, `Date of birth must be a past date in yyyy-MM-dd format`, `State must be two letters`, `postalCode must be at most 10 characters`, `Invalid id`, `That email address is already in use`, `Customer has policies and cannot be deleted`. Compared as a set; order is not part of the contract.
- **Status codes:** unknown role 403, no session 302 to exactly `/login.do` (no query), validation failure 200 with the form again, create and update 302 to `/customers/view.do?id=N`, delete 302 to `/customers/list.do`. Unknown, malformed or missing ids are **404, never 400**. The role check runs **before** the existence check and before validation (an auditor gets 403 on `edit.do?id=99999`).
- **Paging and search:** page size ten; a missing, empty, non-numeric, zero or negative page means page one; ` 2 ` and `+2` mean page two; a page past the end is an empty results page. Search is case-insensitive, trimmed, per field (a full name finds nothing), and `%` and `_` stay wildcards. The claim status filter is exact and trimmed but not case-folded. Order is last name, first name, then id for customers and id for claims. Pager links are compared by their **decoded** parameters, because `+` and `%20` are the same space.

## How the tests stay independent of shared data

- Seeded rows are read-only. Assertions on them are exact values or **ordered prefixes**, never totals, because other suites add rows.
- Every row a test creates carries a per-run token in its last name and e-mail, so sort-sensitive tests search for the token and see a data set nobody else touches. Names are ASCII, capitalised and never contain seeded fragments.
- Fixtures are built over HTTP through the routes that stay on legacy in M6 (policy and claim forms), not through SQL, so the suite survives the later database split. Dates are fixed past dates, never "now".
- Cleanup is best effort; the suite passes on leftovers (CI runs it twice, the second time without a reset) and in any order.

## Why the tests can run unchanged against modern

- **Writes go through the form**, the way a browser does: fetch the form, take every field including hidden ones, override some, submit to the form's own action. If the migrated app adds an anti-CSRF token as a hidden field, the same code carries it. Deleting follows the page's own `#delete-link`.
- **Redirects are checked by path and query and by origin.** A `Location` that points at another host or port fails the test. The legacy app answers with relative locations; a proxy that rewrites `Host` without the port would break browsers, so the gateway must pass the original `Host` including the port (`proxy_set_header Host $http_host`, plus the forwarded headers; Boot needs `server.forward-headers-strategy=native`). No `;jsessionid` may appear in a `Location`.
- **Requests send `Accept: text/html`** so Spring Security redirects to login instead of answering 401 or 403.
- **No cookie is named in a test**, and no test depends on how the session is carried.
- **Gateway routes:** SPEC's `/claims/search/**` does not match the real path `/claims/search.do`. Route `/customers/` by prefix and `location = /claims/search.do` exactly. Keep `proxy_pass` free of a URI part so `%25`, `%2B` and `+` reach the app unchanged.

## The closed list of divergences

`KnownDivergencesTest` is the only class allowed to look at the target. Each entry pins a concrete expectation for **both** targets (never "skip on modern"), and the list length is asserted, so adding one is a reviewed change. `ContractGuardTest` fails the build if anything else branches on the target, skips or disables a test.

1. Customer search term is injectable in legacy; in modern the payload finds nothing.
2. Claim search term: the same.
3. Claim status filter: the same.
4. An apostrophe in a customer search crashes legacy (500); modern answers 200.
5. An apostrophe in a claim search term or status: the same.
6. A POST without an anti-CSRF token succeeds in legacy; modern answers 403 and creates nothing.
7. Deleting with a plain GET works in legacy; modern refuses it (403 or 405) and the row survives.

Every divergence has a test of the **shared** behaviour on the same route elsewhere, so the divergence cannot hide a regression.

## Proving the tests can fail

`characterization-tests/mutants/run-mutants.sh` applies one deliberate defect at a time to a throwaway copy of the legacy app, builds it, and expects the suite to go red. The result table is generated into `characterization-tests/mutants/RESULTS.md`; a surviving mutant is a gap in the tests.

## Found by building this

The first run found a real legacy defect the M2 smoke test had missed: `/policy/view.do` threw a `JspException` half way through rendering for the roles that can renew or cancel (two mappings had no form bean for `html:form`). The status was already sent, so the page answered 200 with a truncated body. Fixed with plain forms, and the smoke test now checks that every page renders to its closing tag.

## What the migrated app must do so the shared tests stay unchanged (M6)

Status in M6: all of the following are implemented, and the suite passes unchanged against both targets.

- **Delete mechanism.** The tests discover how to delete from the administrator's customer page: `#delete-link` as a link means a GET, as a form (or a control inside a form) means a POST to its action with its fields. Tokens come from the acting user's own pages. So the migrated app may turn delete into a POST form without changing a test; a plain GET delete is then refused (divergence 7).
- **Anonymous requests.** An unauthenticated POST (no token possible) must still get `302` to `/login.do`. A CSRF filter alone would answer 403 first, so the migrated app needs an access-denied handler that redirects unauthenticated requests to the login page and answers 403 only to signed-in users.
- **Forbidden roles and tokens.** A forbidden role cannot always obtain a token over HTTP (it sees no POST form), so on a CSRF-protected target the 403 of a forbidden save may come from the token check. The shared test still proves nothing is stored and the request is refused; the role check itself is additionally covered by MockMvc security tests in `modern-app`.
- **Readiness markup.** `scripts/wait-ready.sh` reads `id="total">N` from the customer search page, so the migrated page must render the total the same way.

## Corrections made while building M6

- `CustomerViewTest` first asserted that `#delete-link` carries `href="/customers/delete.do?id=1"`. That encodes the legacy mechanism (a link) and contradicted this ADR ("delete may become a form without changing a test"). It now asserts the same target and customer id as either a link `href` or a form `action` plus its `id` field. The check is not weaker: it still pins where the control points and which customer it names. Raised by the M6 design review.
- Added, and verified against legacy first: ids that Java would parse differently from Spring (`0x1`, `#1`, `1 2` are 404; `+1`, ` 1 ` and `01` are accepted), a save with `id=0` creates a customer, and today is a valid date of birth.
- Deliberately **not** pinned: a date of birth such as `1980-01-1x` makes legacy answer 500 (its validator reads a prefix, then the database layer rejects it). The migrated app answers with the validation message. That is an unpinned improvement, not a divergence the suite checks.

## Risks recorded for M6

- E-mail uniqueness is checked case-insensitively in code, but the database constraint is case-sensitive and the check-then-insert is not atomic, so two concurrent creates that differ only in case can both succeed. Not tested (not deterministic); the migrated app should enforce it in the database.
- If the migrated app adds optimistic locking on customers, last-write-wins (legacy behaviour) changes; that would be a declared divergence.
