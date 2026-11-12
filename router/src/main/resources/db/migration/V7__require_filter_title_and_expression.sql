-- A filter is now authored in the web app: it needs a name to be listed by, and an expression,
-- which V1 left nullable. The table is empty after V6, so NOT NULL needs no default.
ALTER TABLE filter ADD COLUMN title VARCHAR(255) NOT NULL;
ALTER TABLE filter ALTER COLUMN filter SET NOT NULL;
