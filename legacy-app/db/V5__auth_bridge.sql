-- M6 auth bridge: each login gets a random id (sid) in its signed cookie. A logout records that sid here, and the modern app
-- refuses any cookie whose sid is listed. Revocation is per login, not per user: logging out of one browser must not end the
-- user's other logins. Rows only matter until the cookie lifetime cap, so old ones are deleted when a logout happens.
CREATE TABLE auth_revocations (
    sid           VARCHAR(40) PRIMARY KEY,
    revoked_at_ms BIGINT NOT NULL
);
