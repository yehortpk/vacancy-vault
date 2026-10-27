-- Roles: every account is a USER; an ADMIN may additionally start the parser and watch its
-- progress. There is no endpoint that grants ADMIN - it is assigned directly in the
-- database, so registering can never escalate an account.
--
-- Stored as the enum name rather than a lookup table: there are two fixed values and the
-- application maps them with @Enumerated(STRING). The CHECK keeps a typo out of the column.

ALTER TABLE app_user
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'));
