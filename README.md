# legacy-java-modernization-kit

Work in progress. A fictional legacy Struts/Java 8 app, an assessment toolkit and a strangler-fig migration to Spring Boot 3. The full README is written at release.

> **Deliberately insecure for demonstration. Do not deploy.** `legacy-app/` carries seeded security flaws on purpose. All companies, people and data are fictional.

## Quickstart

```bash
cp .env.example .env
docker compose up -d --build postgres legacy
open http://localhost:8081/        # legacy portal
```

Demo logins (plaintext on purpose, fictional): `admin`, `adjuster1`, `adjuster2`, `supervisor1`, `underwriter1`, `csr1`, `auditor1`. The password is the username plus `-demo`.

```bash
scripts/smoke-legacy.sh        # exercises login, customers, claims, policies, reports, admin, the job and the seeded flaws
scripts/reset-db.sh            # truncate and reseed the demo database
```

Business rules and URL shapes for the legacy app are in [docs/adr/0001-legacy-business-rules.md](docs/adr/0001-legacy-business-rules.md).

The nightly policy-expiry job is off by default (`HARBOR_JOBS_ENABLED=false`) because one real run lapses every seeded policy. See the ADR.
