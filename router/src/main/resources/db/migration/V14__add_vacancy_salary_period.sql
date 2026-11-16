-- How often the salary is paid out, e.g. MONTH; NULL when the source does not say.
ALTER TABLE vacancy ADD COLUMN salary_period VARCHAR(16);
