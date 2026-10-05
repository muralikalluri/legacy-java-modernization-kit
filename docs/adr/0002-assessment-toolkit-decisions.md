# ADR 0002: Assessment toolkit decisions (M3)

Status: accepted. Where this differs from SPEC.md, this file is the record.

## Deviations from SPEC §3

1. **`assess deps` queries the OSV HTTP API directly.** SPEC names OWASP Dependency-Check or OSV-Scanner. Neither is installed in CI or locally, Dependency-Check needs a large NVD download, and the OSV API gives the same open data with a stable per-package answer. Each query is cached as one file under `assess/osv-cache/Maven/<group>/<artifact>/<version>.json` and committed, so `./assess/run-all.sh --offline` reproduces the committed results with no network.
2. **`assess jakarta` runs `JavaxMigrationToJakarta`, not `UpgradeSpringBoot_3_x`.** The legacy app has no Spring, so the Spring Boot recipe does not apply. The plugin (`rewrite-maven-plugin 6.46.1`) and the recipe artifact (`rewrite-migrate-java 3.42.1`) are pinned and recorded in `jakarta.json`.
3. **The dry run runs on a copy outside the repository.** OpenRewrite skips git-ignored paths, so a copy under `target/` parses zero files and reports "no changes" with exit code 0. The first attempt did exactly that. The runner now uses a temp directory, runs without `-q`, and refuses to treat a run without a result line as zero changes. `legacy-app/` is never modified and no `legacy-app/target/rewrite` is created. The patch is kept as evidence in `assess/results/jakarta-dryrun.patch`.

## What the numbers mean

- **There is no Jakarta-compatible Struts 1.** After the rewrite, `struts-core` still needs `javax.servlet`. The changed-file count measures the javax surface of this code base, not whether the framework can be moved. The report must say so.
- JDK-owned javax packages (`javax.sql`, `javax.naming`, `javax.xml.parsers`, ...) stay as they are and are counted separately from the Jakarta-migrating ones.
- OpenRewrite does not parse JSPs. `jakarta.json` reports JSP javax references next to the rewrite result so the gap is visible.
- `deps.json` counts findings (library x advisory) and unique advisories separately. `uniqueAdvisories` also includes advisories on provided-scope libraries; `uniqueAdvisoriesInWar` counts only those affecting a jar in the WAR, so use that one when talking about the WAR. Severity is the CVSS v3 base score computed from the OSV vector (`cvss-v3-computed`), else the GHSA label (`ghsa-label`), else `UNKNOWN`. A record with only a v4 vector and no GHSA label is `UNKNOWN`, never 0 or LOW. "No OSV records" is reported as such and is not "clean".
- Libraries come from the WAR (see `deps.json` for the count). Jars without `pom.properties` are identified through `assess/reference/jar-coordinates.yaml`; anything still unknown is listed as unidentified. Identical jars (same SHA-1) are counted once. Provided-scope dependencies (servlet API, PostgreSQL driver) are not in the WAR but run in Tomcat, so they are reported in a separate `provided` section.
- EOL status comes from `assess/reference/eol.yaml` and is evaluated against the table's own `asOf` date, never the clock.

## Counting rules

- Actions: every `<action>` element in `struts-config.xml`, ForwardAction included. The count is pinned by `GoldenTest` and `StrutsConfigTest`. Custom action classes are counted separately (`distinctCustomActionClasses`, the Struts-provided `ForwardAction` excluded), because some classes are mapped twice. M4's estimate must say whether effort is per mapping or per class.
- JSPs: every `*.jsp` under `legacy-app/src/main/webapp`.
- Scriptlets: every `<%`, `<%=` and `<%!` occurrence (not every line), plus the `<jsp:scriptlet|expression|declaration>` forms. `<%@` directives, `<%-- --%>` comments and `<\%` are not counted. Code inside an HTML comment still runs, so it counts.
- `roles=`: `rolesDeclared` is `null` when the attribute is absent and a list (possibly empty) when present. A missing `roles=` means three different things (public, any signed-in user, a seeded flaw), so any security rule must be scoped (for example "under `/admin/` and no `roles=`").
- Modules come from one file, `assess/modules.yaml`, shared by inventory, structure and estimate. Anything it does not cover is an error. Login, logout and home are the **Platform** module, so the actions split across six modules (the split is asserted in `GoldenTest` and listed in `inventory.json`).
- Migration scope (M6): all Customers actions plus `/claims/search`.

## Determinism

No timestamps, no absolute paths and no git SHA in results. Arrays are sorted, except filter order, which is meaningful. JSON is written with fixed formatting and atomically. Every output carries an `inputsFingerprint` so a mix of fresh and stale files is detectable. `dataAsOf` comes from the OSV cache, not from the clock.

## Known gap: gateway route

SPEC §5 routes `/claims/search/**`, but the legacy action is `/claims/search.do`, which that prefix does not match. The M6 gateway routes `/customers/**` and `/claims/search.do`.
