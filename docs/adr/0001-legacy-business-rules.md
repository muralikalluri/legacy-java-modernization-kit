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

Coarse role checks use the Struts `roles=` attribute on the action mapping. The login filter wraps the request so `isUserInRole` reads the session user's single role. A role outside the list gets **HTTP 403**. Fine-grained, per-transition rules live in `ClaimRules` and surface as validation messages (HTTP 200 on the input page).

Object-level access: any signed-in user may view any claim or customer by id (no assignment scoping). AUDITOR has no mutating role anywhere.

## Responses

- No session on a protected path: 302 to `/login.do`. No return-to URL.
- Unknown `id` on a view/edit/transition path: **404**.
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
- Customer search matches first name, last name or email. Claim search matches claim number or customer last name. Claim `status` filter is exact and upper-case. An unknown status returns zero rows.
- `%` and `_` typed by the user act as SQL wildcards in legacy. The modern app keeps that behaviour.
- Page size is 10. A page that is missing, non-numeric or below 1 is treated as page 1. A page past the end shows no rows (HTTP 200). `total` on an out-of-range page is unspecified, so tests do not assert it.
- Orders are deterministic: customers by last name, first name, id; claims by id.

## Determinism

- JVM runs with `-Duser.timezone=UTC`. Dates are DATE columns formatted `yyyy-MM-dd`. Money is shown as `1,234.50` (US locale, 2 decimals), set explicitly.
- Form fields are Strings and parsed by hand, so a blank amount never silently becomes 0.
- Tomcat uses cookie-only session tracking (no `;jsessionid` in URLs) and a UTF-8 request filter.
- Seed uses explicit ids and fixed dates, and the sequences are advanced after. `scripts/reset-db.sh` truncates and reseeds. Seed text is ASCII and free of `& < > " '`.

## Seeded flaws

Concatenated SQL: `CustomerDao.search`, `ClaimDao.search` (one query site each, one window-count query so the statement is built once). The third site arrives in M2. The app connects as `harbor_app`, a non-superuser with DML only, so the demo stays exploitable but contained. No CSRF tokens on any form. Passwords are plaintext in `app_users`.
