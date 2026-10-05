# ADR 0005: Auth bridge, gateway and the modern app (M6)

Status: accepted. Where this differs from SPEC §5, this file is the record.

## What was built

A gateway (nginx) in front of two apps that share one PostgreSQL database. `/customers/` and exactly `/claims/search.do` go to the Spring Boot 3 app; everything else goes to the legacy Struts app. The two apps accept the same login through a signed cookie. The characterisation suite (ADR 0004) runs unchanged against both.

## Deviations from SPEC §5

1. **No separate "shared auth endpoint".** Legacy stays the only place anyone signs in and also issues the cookie. A second endpoint would be a second place to get authentication wrong.
2. **The claim search route is `/claims/search.do`, matched exactly.** SPEC's `/claims/search/**` does not match the real path. `/customers/` is matched as a prefix with `^~`, so a regex location added later cannot take it over; `/customers` without the slash is not migrated.
3. **`JdbcClient` instead of Spring Data JDBC or JPA.** Every statement is parameterised SQL over the legacy schema, which maps straight onto the framework mapping table in SPEC §1 and keeps the migration a like-for-like move.
4. **Flyway runs as a one-shot compose service with the owner account**, not inside the app, so the app containers never hold DDL rights. The legacy schema is the baseline (version 5, which includes the auth bridge table); the modern migrations start at version 6. `baselineOnMigrate` only baselines a non-empty schema, so on an empty database the first modern migration fails loudly instead of pretending to succeed (tested).
5. **The legacy schema gained one table (`auth_revocations`, legacy script V5)**, because logout belongs to legacy and must be visible to the modern app.

## The cookie

`HARBOR_AUTH = v1.<base64url(payload)>.<base64url(HMAC-SHA256 over "v1.<payload>")>`. The payload names **who** (`u`), **which login** (`sid`, 128 random bits), **when that login happened** (`auth`), and the issue and expiry times (`iat`, `exp`, milliseconds). It carries **no role**. HttpOnly, SameSite=Lax, Path=/, no Domain; `Secure` comes from configuration, never from a request header.

- **Verification order:** length cap, exactly three parts, the literal version `v1`, base64url alphabet, **then the MAC over the exact received bytes**, then the JSON (duplicate keys, unknown keys and trailing text are refused), then the user-name and sid alphabets. Comparison is constant time.
- **Keys:** separate keys for the cookie and for CSRF are derived from one secret (`HMAC(secret, label)`). The secret comes from the environment (not a `-D` flag, which shows up in `ps` and `docker inspect`), must be at least 32 **bytes**, and the app refuses to start with the `.env.example` placeholder unless a demo switch is set. A previous secret can be accepted during rotation; new cookies are always signed with the current one.
- **Time rules:** a cookie expires after a 30-minute idle window; it is refreshed (by either app) in the second half of that window, but never beyond a hard cap counted from the login (`auth`). A refresh keeps `auth` and `sid`, so it can never move a login. About a minute of clock skew is tolerated on `iat` and `auth`.
- **What the apps trust:** legacy only signs. Modern verifies, and also signs refreshed cookies. A shared test vector produced by an independent HMAC implementation lives in both test suites.

## Identity from the cookie, authority from the database

On every request modern looks the user up: the **role and the active flag come from the database**, never from the cookie. So a role injected into a cookie (the legacy admin flaw lets any signed-in user create users with any name) cannot become authority, a deactivated user is refused at once, and a role change takes effect on the next request. Legacy keeps the role it cached in the session at login, so a role change reaches legacy at the next login: a known, tolerated difference.

## Logout and revocation

Logout is per **login**, not per user. Legacy writes the login's `sid` into `auth_revocations` and clears the cookie (same name, path and attributes, no Domain). Modern refuses any cookie whose `sid` is listed, so a stolen copy stops working too, and a late refresh cannot bring a logged-out login back, because a refresh keeps the `sid`. A user's other logins are untouched. This was first built as a per-user "logout everywhere" stamp; the characterisation suite (several independent logins of one user) showed it ended the user's other logins on modern only, so it became per login. Rows older than the lifetime cap are deleted at logout.

Limits: a login that simply times out is never logged out, so its cookie lives until it expires (idle window, or the cap with activity). Revocation depends on legacy writing the row; if that write fails, logout still succeeds (best effort, logged).

## CSRF, redirects, errors, sessions

- **CSRF token bound to the login**, not stored in a cookie or a session: an HMAC of the user name and `sid`. Cookies are not scoped by port on one host, so a cookie-based double submit could be forged by a sibling app that plants cookies; this token cannot be computed without the key, and it stays valid across cookie refreshes. Forms carry it as a hidden field (masked per request by Spring).
- **Anonymous callers** (including a POST whose CSRF check failed) get `302 /login.do` with a relative Location and no query. Signed-in callers get 403. Both handlers set the status directly: `sendError` would trigger an error dispatch that the stateless filter treats as anonymous, turning a 404 into a login redirect.
- **Role rules run in the filter chain**, before any controller, so the role check comes before the existence check and before validation, and a bad `Content-Type` or id can never turn a 403 into a 400. Method security on the service layer is a second line. Ids are Strings parsed like legacy (`parseInt` of the trimmed value), so `+1` and ` 1 ` work and `0x1`, `#1` and `1 2` are 404, never 400.
- **No HTTP session, ever.** Both apps share one host and the path `/`, so a modern `JSESSIONID` would replace legacy's and sign the user out of legacy. The stateless filter chain, no flash attributes and a renamed session cookie guard against it, and a smoke check asserts that no modern response sets a session or CSRF cookie.
- **Delete is a POST form** (`#delete-link`); a GET delete is refused (divergence 7).

## Gateway

Host is passed exactly as sent, port included. Client-supplied `X-Forwarded-*` and `Forwarded` headers are cleared, `X-Forwarded-For` is overwritten (never appended), and the gateway announces no protocol or port, so neither app builds an absolute URL from a spoofable header and redirects stay relative. Published ports are bound to 127.0.0.1. The gateway has a healthcheck that fails on an invalid config or a down upstream.

## Inherited, not fixed

The legacy admin flaws remain and are visible through the bridge: any signed-in user can call `/admin/userSave` (so can create an administrator account) and `/admin/jobRun`. The modern app does not weaken or hide them; they belong to Phase 0 of the roadmap. The modern app fixes the flaws in the modules it migrated (parameterised SQL, CSRF, method security).

## Not covered

- TLS: the demo is plain http. For https set `HARBOR_COOKIE_SECURE=true` and use `__Host-` cookie names.
- A `jti`-style one-time token or server-side session store would allow immediate global revocation; the revocation table is the smaller step.
- A modern pod restart loses nothing (no state); nginx resolves its upstreams at start, so restart it after replacing an app container.
