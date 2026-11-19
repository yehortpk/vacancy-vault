-- A stored vacancy keeps the company it arrived with, so nothing looks vacancies up by their
-- company any more: the index only served the update of a renamed company's rows.
DROP INDEX idx_vacancy_company_id;
