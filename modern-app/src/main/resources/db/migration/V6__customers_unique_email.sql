-- Modern-owned migration (the legacy schema is baselined at version 5).
-- The legacy app checks e-mail uniqueness in code, case-insensitively, but its constraint is case-sensitive and
-- check-then-insert is not atomic. This index makes the database enforce what the code promises.
-- It fails, loudly, if the data already holds two customers whose e-mails differ only in case, and if the table is missing.
CREATE UNIQUE INDEX uq_customers_email_ci ON customers (lower(email));
