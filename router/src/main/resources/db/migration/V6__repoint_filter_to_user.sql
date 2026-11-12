-- Filters belong to accounts instead of Telegram chats. No backfill: the old rows go. Dropping
-- client_id also drops its foreign key and idx_filter_client_id.
DELETE FROM filter;

ALTER TABLE filter DROP COLUMN client_id;
ALTER TABLE filter ADD COLUMN user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE;

CREATE INDEX idx_filter_user_id ON filter (user_id);
