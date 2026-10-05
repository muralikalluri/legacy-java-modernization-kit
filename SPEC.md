# SPEC — legacy-java-modernization-kit

> A realistic legacy Struts/J2EE/Java 8 application, an automated assessment toolkit that produces a modernization report, and a strangler-fig migration of two modules to Spring Boot 3 running side by side. No big-bang rewrite.

## 1. Purpose and positioning

Public proof for the Upwork listing **"Legacy Java to Spring Boot 3 — modernization assessment and roadmap"** ($300 / $800 / $1,800).

| Listing claim | Proof in this repo |
|---|---|
| Code and dependency scan, key risks (Starter) | `assess` CLI runs inventory + CVE scan + Jakarta impact on `legacy-app/` |
| What to migrate first, what to leave alone | Module scoring (business value × risk × coupling) → migration order in report |
| Framework mappings | Mapping table: Struts Action → `@Controller`, ActionForm → DTO + Bean Validation, Tiles → Thymeleaf layout, JDBC DAO → Spring Data/JdbcClient, EJB → `@Service`, JNDI DataSource → Boot config, web.xml filters → `SecurityFilterChain` |
| Security gaps to close along the way | CVE findings (old Struts, log4j 1.x, commons-collections), homegrown auth, SQL string concatenation, missing CSRF |
| Realistic effort estimate | Estimation model from inventory counts, with ranges and assumptions |
| No big-bang rewrite | Legacy app and new Boot 3 app run together behind one gateway; 2 modules migrated, the rest still legacy |

Everything is synthetic. The legacy app is written fresh for this repo and must not resemble any real client system.

## 2. The legacy target: "Harbor Claims Portal" (fictional insurer)

A deliberately dated but working app:

| Aspect | Legacy choice |
|---|---|
| Java / container | Java 8, Tomcat 8.5/9 (Docker image), WAR packaging |
| Web framework | Struts 1.3.10, Tiles, JSP with scriptlets in places |
| Data access | Plain JDBC DAOs with string-concatenated SQL in some places; JNDI DataSource |
| Business layer | "Manager" singletons + one EJB-style session facade simulated with POJOs |
| Security | Homegrown login filter, session role checks inside actions, roles in a DB table |
| Logging | log4j 1.2 |
| Other | Quartz 1.x nightly batch, iText 2 PDF export, commons-collections 3.2.1, Ant-style build ported to Maven for convenience |
| DB | PostgreSQL (seeded) |

Size target (enough to make the assessment meaningful, small enough for an MVP): ~35 Struts actions across 5 modules (Claims, Policies, Customers, Reports, Admin), ~20 JSPs, 6 roles, 1 batch job, 1 PDF report.

Seeded issues for the assessment to find: 3 SQL-injectable queries, missing CSRF, plaintext password comparison, role check missing on 2 admin actions, vulnerable dependencies, `javax.*` usage throughout, a God-class `ClaimManager` (1,000+ lines), cyclic package dependency between Claims and Policies.

## 3. Assessment toolkit (`assess/`)

A Java 21 CLI (picocli) or Python CLI — choose Java to match the audience.

| Command | What it produces |
|---|---|
| `assess inventory` | Parses `struts-config.xml`, `web.xml`, tiles defs, JSPs: actions, forms, forwards, filters, JSP count, scriptlet count, roles per action → `inventory.json` |
| `assess deps` | Runs OWASP Dependency-Check (or OSV-Scanner) on the WAR → CVE table with severity; flags EOL frameworks |
| `assess jakarta` | Counts `javax.*` imports by package; runs OpenRewrite `UpgradeSpringBoot_3_x` / Jakarta recipes in **dry-run** and reports changed-file counts |
| `assess structure` | jdeps + simple metrics: package cycles, largest classes, fan-in/fan-out per module (ArchUnit or jdepend) |
| `assess security` | Semgrep with a small ruleset: SQL concat, missing CSRF token, plaintext password compare, missing role checks (cross-referenced with inventory) |
| `assess estimate` | Applies `estimation-model.yaml` (effort per action/JSP/DAO/job, complexity multipliers) → effort ranges per module and phase |
| `assess report` | Renders everything into `report/ASSESSMENT.md` (+ PDF via pandoc) using templates |

All outputs are generated. Nothing in the report's numbers is typed by hand.

## 4. Assessment report structure (the sellable artefact)

`sample-deliverable/`:

| File | Matches tier | Content |
|---|---|---|
| `QUICK_ASSESSMENT.md` | Starter ($300) | Inventory summary, top 10 risks, CVE summary, high-level approach (strangler vs rewrite vs lift), rough effort band |
| `ASSESSMENT_AND_ROADMAP.md` | Standard/Advanced | Full report below |

Full report sections:
1. Executive summary: recommendation, phases, effort range, top risks
2. System inventory: modules, actions, roles, integrations (tables from `inventory.json`)
3. Risk register: security, EOL, structural, knowledge risks, each with likelihood/impact
4. Framework mapping table (legacy construct → Spring Boot 3 equivalent → migration notes)
5. Module scoring and migration order: migrate first / later / leave alone (and why)
6. Target architecture (Mermaid): gateway, legacy WAR, Boot 3 app, shared DB, then DB split later
7. Phased roadmap: Phase 0 (security quick fixes in legacy), Phase 1 (platform + first module), Phase 2…n
8. Effort estimate: per module and phase, ranges, assumptions, team shape
9. Security gaps to close during migration
10. Appendix: raw tool outputs

## 5. Strangler-fig migration (`modern-app/`)

- Spring Boot 3.x, Java 21, Spring Security 6, Thymeleaf, Spring Data JDBC or JPA, Flyway (baseline on existing schema), Testcontainers.
- Migrate **Customers** (simple CRUD) and **Claims search** (read-heavy) modules.
- Gateway (Spring Cloud Gateway or nginx) routes `/customers/**` and `/claims/search/**` to Boot 3; everything else to legacy Tomcat.
- Session/auth bridge: both apps accept the same login. MVP: Boot app validates a signed cookie issued by a small shared auth endpoint. Document the approach and its trade-offs in an ADR.
- **Characterisation tests first**: Playwright/HTTP tests capture legacy behaviour for the two modules, then run unchanged against the migrated routes. This is the key proof of "safe migration".
- Seeded security issues in migrated modules are fixed (parameterised queries, CSRF, method security).

## 6. Repository layout

```
legacy-java-modernization-kit/
├── legacy-app/                 # Struts 1 WAR, Java 8, Dockerfile (Tomcat)
├── modern-app/                 # Spring Boot 3, Java 21
├── gateway/                    # routing config
├── assess/                     # CLI toolkit + templates + estimation-model.yaml
├── characterization-tests/     # run against legacy and modern routes
├── sample-deliverable/         # QUICK_ASSESSMENT.md, ASSESSMENT_AND_ROADMAP.md (+ PDFs)
├── docker-compose.yml          # postgres, legacy, modern, gateway
└── docs/adr/
```

## 7. Milestones

| M | Scope | Done when | MVP? |
|---|---|---|---|
| M0 | Repo, compose, CI (builds Java 8 legacy and Java 21 modern in separate jobs) | Both build | ✅ |
| M1 | Legacy app: schema + seed, login, Customers + Claims modules with seeded issues | Runs in Tomcat container | ✅ |
| M2 | Legacy app: Policies, Reports (PDF), Admin, Quartz job → ~35 actions | Inventory count matches target | ✅ |
| M3 | `assess inventory`, `deps`, `jakarta` | JSON outputs generated | ✅ |
| M4 | `assess structure`, `security`, `estimate`, `report` | QUICK_ASSESSMENT.md generated end to end | ✅ |
| M5 | Characterisation tests for Customers + Claims search against legacy | Green against legacy | ✅ |
| M6 | Modern app with the two migrated modules + gateway + auth bridge | Same tests green through gateway | ✅ |
| M7 | Full ASSESSMENT_AND_ROADMAP.md + PDFs + README + GIF | Portfolio checklist met | ✅ |
| M8 | OpenRewrite actually applied to one module in a branch, diff shown in README | Later | — |
