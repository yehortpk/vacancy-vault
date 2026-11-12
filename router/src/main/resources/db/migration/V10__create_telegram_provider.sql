-- The Telegram provider: its catalog row and its details table. provider_id is fixed to 1 and
-- part of the foreign key, so a row here can only belong to a Telegram connection. The UNIQUE
-- on chat_id makes one chat belong to at most one account.
INSERT INTO provider (id, title, link_instructions) VALUES (
    1,
    'Telegram',
    'Send /link {token} to the Telegram bot from the chat that should receive notifications.'
);

CREATE TABLE telegram_connection (
    connection_id BIGINT NOT NULL,
    provider_id   BIGINT NOT NULL DEFAULT 1,
    chat_id       BIGINT NOT NULL,
    PRIMARY KEY (connection_id),
    CONSTRAINT fk_telegram_connection_connection FOREIGN KEY (connection_id, provider_id)
        REFERENCES connection (id, provider_id) ON DELETE CASCADE,
    CONSTRAINT ck_telegram_connection_provider_id CHECK (provider_id = 1),
    CONSTRAINT uk_telegram_connection_chat_id UNIQUE (chat_id)
);
