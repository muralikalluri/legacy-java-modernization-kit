# ADR 0001: Legacy business rules, URLs and test-stability decisions

Status: accepted (M1). Characterisation tests (M5) and the modern app (M6) are checked against this file.

> Deliberately insecure for demonstration. Do not deploy. The legacy app keeps its seeded flaws on purpose.

## URL shapes

Paths are chosen so the gateway can route by prefix: `/customers/**` and `/claims/search.do` go to the modern app later. Claim detail and transitions live under `/claim/` (singular) and stay on legacy.

| Path | Purpose | Roles (struts `roles=`) |
|---|---|---|
| `/login.do`, `/loginSubmit.do`, `/logout.do` | auth | public |
| `/home.do` | landing page | any signed-in user |
| `/customers/list.do`, `/customers/search.do` | paged customer search (`q`, `page`) | any signed-in user |
| `/customers/view.do?id=` | customer detail | any signed-in user |
| `/customers/edit.do` | edit or new form | ADMIN, SUPERVISOR, CSR |
| `/customers/save.do` | create or update | ADMIN, SUPERVISOR, CSR |
| `/customers/delete.do?id=` | delete (blocked if policies exist) | ADMIN |
| `/claims/search.do` | paged claim search (`q`, `status`, `page`) | any signed-in user |
| `/claim/view.do?id=` | claim detail | any signed-in user |
| `/claim/new.do`, `/claim/save.do` | create a claim | ADMIN, ADJUSTER, CSR |
| `/claim/transition.do` | status change | ADMIN, ADJUSTER, SUPERVISOR |
| `/claim/note.do` | add a note | ADMIN, ADJUSTER, SUPERVISOR, CSR |
| `/policies/list.do`, `/policies/search.do` | paged policy search (`q`, `status`, `page`) | any signed-in user |
| `/policy/view.do?id=` | policy detail with its claims | any signed-in user |
| `/policy/edit.do`, `/policy/save.do` | create or edit terms | ADMIN, UNDERWRITER |
| `/policy/renew.do` | extend by one year | ADMIN, UNDERWRITER |
| `/policy/cancel.do` | cancel | ADMIN, UNDERWRITER, SUPERVISOR |
| `/reports/index.do`, `claimsByStatus.do`, `claimsByStatusPdf.do`, `policyExpiry.do`, `claimsByAdjuster.do` | reports | ADMIN, SUPERVISOR, AUDITOR |
| `/admin/index.do`, `users.do`, `userEdit.do`, `userToggle.do`, `jobs.do` | administration | ADMIN |
| `/admin/userSave.do`, `/admin/jobRun.do` | save a user, run the expiry job now | **none (seeded flaws)** |

There are exactly **35** action mappings. The counting rule is "every `<action>` element in `struts-config.xml`, ForwardAction included". There is no DispatchAction and there is one Struts module. `StrutsConfigTest` enforces the count.

Coarse role checks use the Struts `roles=` attribute on the action mapping. The login filter wraps the request so `isUserInRole` reads the session user's single role. A role outside the list gets **HTTP 403**. Fine-grained, per-transition rules live in `ClaimRules` and surface as validation messages (HTTP 200 on the input page).

Object-level access: any signed-in user may view any claim or customer by id (no assignment scoping). AUDITOR has no mutating role anywhere.

## Responses

- No session on a protected path: 302 to `/login.do`. No return-to URL.
- Unknown, malformed or missing `id` on a view, edit, delete or transition path, and a save for an unknown id: **404** (never 400).
- Validation or business-rule failure: the input page again, HTTP 200, messages inside `<div id="errors">`.
- Unhandled exception (including SQL errors): generic 500 page with no stack trace.
- Login failure: one message for unknown user, wrong password or inactive user. Username is trimmed and case-sensitive.
- Stable test hooks in every page: `id="results"`, `id="total"`, `data-claim-id`, `data-customer-id`, `data-status`, `id="errors"`. Tests assert on these and on status codes, never on layout markup.

## Claim status machine

| From | To | Allowed roles |
|---|---|---|
| NEW | UNDER_REVIEW | ADMIN, ADJUSTER, SUPERVISOR |
| UNDER_REVIEW | APPROVED | ADMIN, SUPERVISOR; ADJUSTER only if claimed amount <= 10000.00 |
| UNDER_REVIEW | DENIED | ADMIN, ADJUSTER, SUPERVISOR (reason required) |
| APPROVED | PAID | ADMIN, SUPERVISOR |
| DENIED | REOPENED | ADMIN, SUPERVISOR |
| REOPENED | UNDER_REVIEW | ADMIN, ADJUSTER, SUPERVISOR |

PAID is terminal. APPROVED cannot be reopened. Same-state and any other edge are rejected as invalid. The threshold test is `amount > 10000.00` (equal is adjuster-approvable) and compares the **claimed** amount with `BigDecimal.compareTo`. ADMIN is treated as supervisor-level. Self-approval is allowed (creator is not tracked).

The update is `UPDATE claims SET ... WHERE id = ? AND status = ?`. Zero rows updated means someone else moved the claim first, and the user sees "Claim status has changed. Reload and try again." A double submit of PAID therefore gets a defined error. Every transition writes a note: `Status changed from X to Y` plus the reason when given.

APPROVED accepts an optional approved amount: blank means the claimed amount; otherwise 0 < amount <= claimed.

## Claim creation rules

- Policy is found by policy number and must be ACTIVE.
- Loss date is `yyyy-MM-dd`, within the policy period inclusive on both ends, and not after today (UTC).
- Amount is > 0, at most 2 decimals, and <= the policy coverage limit (equality allowed). The limit applies per claim, not cumulatively.
- Description is required, at most 2000 characters. The customer is taken from the policy. New claims start as NEW with number `CLM-` plus the 6-digit zero-padded id.

## Search and paging

- Case-insensitive contains match. Input is trimmed. Empty means all rows.
- Customer search matches first name, last name or email. Claim search matches claim number or customer last name. Claim `status` filter is exact: the value is trimmed but **not** case-folded, so `paid` returns zero rows. An unknown status returns zero rows.
- `%` and `_` typed by the user act as SQL wildcards in legacy. The modern app keeps that behaviour.
- Page size is 10. A page that is missing, non-numeric or below 1 is treated as page 1. A page past the end shows no rows (HTTP 200). `total` on an out-of-range page is unspecified, so tests do not assert it.
- Orders are deterministic: customers by last name, first name, id; claims by id.

## Determinism

- JVM runs with `-Duser.timezone=UTC`. Dates are DATE columns formatted `yyyy-MM-dd`. Money is shown as `1,234.50` (US locale, 2 decimals), set explicitly.
- Form fields are Strings and parsed by hand, so a blank amount never silently becomes 0.
- Tomcat uses cookie-only session tracking (no `;jsessionid` in URLs) and a UTF-8 request filter.
- Seed uses explicit ids and fixed dates, and the sequences are advanced after. `scripts/reset-db.sh` truncates and reseeds. Seed text is ASCII and free of `& < > " '`.

## Policies

Statuses are ACTIVE, LAPSED and CANCELLED. Policy numbers are `POL-` plus the 6-digit id.

- **In force through the end date, inclusive.** The nightly job lapses ACTIVE policies only when `end_date < asOf`. Known quirk, not fixed: once lapsed, a policy refuses new claims even for a loss inside its period.
- **Cancel** needs an ACTIVE policy with no open claims. Open means NEW, UNDER_REVIEW, REOPENED or APPROVED (approved but unpaid). The cancel date goes in `cancelled_on`, and `end_date` is untouched. The update is `... WHERE id = ? AND status = 'ACTIVE'`, so a lost race gives "Policy status has changed. Reload and try again.". A second cancel from the UI hits the status check first ("Only active policies can be cancelled").
- **Renew** extends the same row, from the old end date (`old_end + 1 year`, so 29 Feb becomes 28 Feb). Allowed from ACTIVE or LAPSED, not CANCELLED. Refused if the new end date is still before today. The form carries the expected end date and the update is `WHERE id = ? AND status = ? AND end_date = ?`, so a double submit gets the stale message.
- **Edit** is limited to ACTIVE policies. Product, coverage limit and premium can change. The customer and the dates cannot change on an existing policy (dates change only through renewal). Lowering the limit below an existing claim is allowed because the limit applies per claim at creation time.
- Double-submitting a new policy creates two rows. That is accepted legacy behaviour. No Struts token is used, so the seeded "missing CSRF" finding stays clean.

## Nightly job

`PolicyExpiryJob` (Quartz 1.8.6, RAMJobStore, one thread) runs `UPDATE policies SET status = 'LAPSED' WHERE status = 'ACTIVE' AND end_date < ?` once a night (`0 0 2 * * ?`, UTC). The admin "run now" calls the same `PolicyExpiryRunner`.

- **Off by default.** The scheduler starts only with `-Dharbor.jobs.enabled=true` (compose: `HARBOR_JOBS_ENABLED`). The seed is a time bomb on purpose: every seeded ACTIVE policy ended before today, so one real run lapses all of them. Nothing runs at startup. Tests use `asOf=2000-01-01`, which lapses nothing, and `scripts/reset-db.sh` restores the seed.
- **Idempotent.** A second run for the same date updates 0 rows.
- **No overlap.** A JVM lock refuses a second run while one is active. "Run now" then redirects to `/admin/jobs.do?busy=1`. Otherwise it redirects to `/admin/jobs.do?run=<id>`, so a refresh never re-runs it.
- **History.** Every run is a row in `job_runs` (RUNNING first, then SUCCEEDED with the row count, committed together with the data, or FAILED with the error). The jobs page shows the last 10.

## Reports

All report SQL is parameterised. Claims by status always lists all six statuses, including zero rows. Claims by adjuster includes an "Unassigned" bucket (last) and inactive users. Policy expiry takes `asOf` (default today UTC) and `days` (default 60, 1 to 365) and is inclusive at both ends, ordered by end date then id. The PDF uses base-14 Helvetica and prints no generation time, so its page text is stable for the same data (the file bytes are not: iText adds dates and an id).

## Admin

`userToggle` refuses to deactivate yourself or the last active admin. A changed role takes effect at that user's next login, because the session caches the user. A failed `userSave` or `jobRun` forwards to an admin-only page, so a non-admin caller sees 403 instead of the validation message (the forward re-checks `roles=`).

## Seeded flaws

Concatenated SQL, three query sites: `CustomerDao.search`, `ClaimDao.search` and `AdminUserDao.search` (one statement each, built once through a window count). Every other query is parameterised, which a smoke test checks for policy search.

Missing role checks on exactly two admin mappings: `/admin/userSave` (any signed-in user can create or change users, including their role: privilege escalation) and `/admin/jobRun`. Both are visible as `<action>` elements under `/admin/` with no `roles=` attribute.

`ClaimManager` is a God class (1,000+ lines) and `claims` and `policies` depend on each other: `ClaimManager` calls `PolicyManager` for policy lookups and `PolicyManager` calls `ClaimManager` for open-claim counts and a policy's claims. The app connects as `harbor_app`, a non-superuser with DML only, so the demo stays exploitable but contained. No CSRF tokens on any form. Passwords are plaintext in `app_users`.
