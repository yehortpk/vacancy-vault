-- The links of the vacancies already matched against the filters. A new_vacancies message
-- whose link is here is a redelivery or a replay and notifies nobody.
CREATE TABLE notified_vacancy (
    link        VARCHAR(255) NOT NULL,
    notified_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (link)
);
