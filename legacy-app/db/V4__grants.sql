-- Least privilege: DML only. No DDL, no superuser, no ownership.
GRANT SELECT ON roles TO harbor_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON app_users, customers, policies, claims, claim_notes, job_runs, auth_revocations TO harbor_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO harbor_app;
