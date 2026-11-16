-- Code of the currency the salary is published in, e.g. USD; NULL when the source does not say.
-- Amounts are kept as published rather than converted, so they stay exact as rates move.
ALTER TABLE vacancy ADD COLUMN salary_currency VARCHAR(16);
