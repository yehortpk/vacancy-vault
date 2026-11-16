-- A salary bound the source did not publish is NULL, not 0. The columns were NOT NULL only
-- because the entity once held them as primitive ints, so 0 stood in for "unknown" until now.
ALTER TABLE vacancy ALTER COLUMN min_salary DROP NOT NULL;
ALTER TABLE vacancy ALTER COLUMN max_salary DROP NOT NULL;

UPDATE vacancy SET min_salary = NULL WHERE min_salary = 0;
UPDATE vacancy SET max_salary = NULL WHERE max_salary = 0;
