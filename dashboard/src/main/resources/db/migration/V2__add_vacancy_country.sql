-- The countries a vacancy is offered in, as upper-case ISO 3166-1 alpha-2 codes. A vacancy can
-- list several, and one the source gave no country for has no rows here.
--
-- The vacancy's rows go with it: removals are bulk deletes that bypass the entity, so the
-- database cascades them.

CREATE TABLE vacancy_country (
    vacancy_id INTEGER    NOT NULL REFERENCES vacancy (vacancy_id) ON DELETE CASCADE,
    country    VARCHAR(2) NOT NULL,
    PRIMARY KEY (vacancy_id, country)
);

-- The listing filters by country; the primary key already serves the lookups by vacancy
CREATE INDEX idx_vacancy_country_country ON vacancy_country (country);
