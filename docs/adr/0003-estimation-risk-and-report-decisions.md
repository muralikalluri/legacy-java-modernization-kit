# ADR 0003: Structure, security, estimate and report decisions (M4)

Status: accepted. Where this differs from SPEC.md, this file is the record. It states rules and thresholds, never results: every result number lives in `assess/results/` and in the generated deliverable.

## Tools

- **Semgrep is a new tool in CI**, pinned to one exact version (`pip install semgrep==...` in `.github/workflows/ci.yml`; the version used is recorded in `security.json`). It runs offline with the local ruleset in `assess/rules/harbor.yaml` (`--metrics=off`, `--disable-version-check`). The alternative was a JavaParser check; Semgrep was kept because the rules read like the findings they report.
- When Semgrep is not installed, `assess security --semgrep reuse` keeps the committed result, but only if a hash of the sources and rules is unchanged, otherwise it fails. The same check guards the reused OpenRewrite result (`--rewrite reuse`). The rule tests that need Semgrep skip themselves when it is absent; CI installs it, so they always run there.
- jdeps reads the compiled classes, so CI builds `legacy-app` (in a temurin-8 container) before the assess job runs. The package graph from bytecode is cross-checked against the import graph; the build fails if the import graph has an edge the bytecode lacks.

## SPEC corrections

1. SPEC §3 says `assess report` writes `report/ASSESSMENT.md`; §4 and §7 say `sample-deliverable/`. Decision: reports are written to `sample-deliverable/` (the quick assessment now, the full report in M7). There is no `report/` directory.
2. SPEC §2 names a cyclic dependency between Claims and Policies. The tool shows it is one component with Customers (Customers and Policies depend on each other), which matters because Customers moves first. There is also a cycle inside Platform (auth and common), which is not charged because Platform is replaced, not migrated.
3. SPEC §3 lists "missing role checks" under Semgrep. It is an inventory cross-reference: an `/admin/` mapping whose `roles=` attribute is absent. "Missing CSRF token" cannot be found from forms alone (GET links mutate too), so it is reported once for the whole app, with the mutating mappings listed.
4. "1,000+ lines" for the God class is counted as physical lines of the compilation unit, with nested classes folded into the outer class, using the same counter as the inventory. The 1,000 threshold comes from SPEC and was not tuned to the seed.

## Estimation model

- The model file (`assess/estimation-model.yaml`) holds inputs only: rates, multipliers, thresholds, business values, team shape and Phase 0 costs, each with a rationale. It holds no counts and no totals (a test fails if a count-like key appears). Hand-set rates are therefore **labelled assumptions, not hand-typed results**; derived totals are never typed anywhere.
- Unit: ideal person-days as a low-high band. Bands add lows and highs separately (a hard band, not a confidence interval; no root-sum-square, because the errors are correlated). Calendar time applies the team shape last.
- Rows are rounded outward (low down, high up) to the model's step, and every total is the sum of the rows shown, so tables add up.
- Build effort is charged per **distinct action class** plus a routing cost per mapping; plain forwards pay routing only. A class mapped twice is charged once. The per-mapping alternative is computed and shown as a delta.
- Multipliers add (`1 + sum`) and are capped. They apply to module build rows only, never to fixed, platform, test or Phase 0 costs.
- A God class and each cycle edge are charged once, in the first phase that touches them.
- The whole app is estimated. Phase 1 is exactly the migration scope in `modules.yaml`. A module with in-scope actions is split: the in-scope slice (with the shared data layer) in Phase 1, the rest later; the two parts add up to the module.
- Modules scored "leave alone" are listed with the cost of migrating them and are **excluded from the total**; the report says so.
- Characterisation tests are charged as their own line per migrated non-forward mapping, never inside the unit rates.
- Phase 0 is charged per finding group, not per CVE, and per library family. A framework with no fixed version gets compensating controls instead of an upgrade. SQL fixes in legacy are later replaced when the DAO is rewritten: deliberate double work, labelled as such.
- A generated sensitivity table shows every rate at half and one and a half times, plus the per-mapping alternative.

## Module scoring

Score = business value (an input; code cannot reveal it) and a coupling measure from `structure.json`: class-level edges crossing the module boundary into other non-Platform modules, in plus out, per class in the module. Cut points are in the model and were fixed before the results were read. Value at or below the leave-alone value means leave alone; otherwise low coupling means migrate first and the rest later, ordered by value times (1 + security sites in the module). If the computed order disagrees with the M6 scope, the report says so instead of re-tuning.

## Risk register

`assess/reference/risk-rules.yaml` holds likelihood and impact (1 to 5) per risk type, each with a rationale. One risk per root cause: N SQL injection sites are one risk; dependencies are grouped by library family; EOL and CVEs on one family are one risk scored at the higher of the two. An `UNKNOWN` severity maps to a defined value, never zero. Order is score, then impact, then group order, then id. The top list takes at most a fixed number from any one group and states what the cap left out and how many entries tie with the last one shown. Knowledge risks cannot be read from code, so they are hand-set inputs labelled as assumptions.

## Report

Numbers, pluralisation and Markdown escaping are produced in Java; the template contains no digits and a missing variable fails the render. The Struts caveat is quoted from `jakarta.json`, and the assumptions are rendered from the model. A test requires the committed `QUICK_ASSESSMENT.md` to equal a fresh render. PDFs are not byte-deterministic (iText and pandoc write dates), so they stay out of the diff gate.
