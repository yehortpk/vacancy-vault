-- The sweeper deletes by age
CREATE INDEX idx_notified_vacancy_notified_at ON notified_vacancy (notified_at);
