-- Test-only provider, so the provider-independent core can be tested before any real messenger
-- exists. Shaped exactly like a real provider's migration: a catalog row with a fixed id, and a
-- details table whose composite foreign key ties each row to a connection of this provider.
--
-- Repeatable, so it runs after every versioned migration and never takes a version number a
-- real migration will need.

INSERT INTO provider (id, title, link_instructions) VALUES (
    99,
    'Stub',
    'Send /link {token} to the stub messenger.'
);

CREATE TABLE stub_connection (
    connection_id BIGINT       NOT NULL,
    provider_id   BIGINT       NOT NULL DEFAULT 99,
    address       VARCHAR(255) NOT NULL,
    PRIMARY KEY (connection_id),
    CONSTRAINT fk_stub_connection_connection FOREIGN KEY (connection_id, provider_id)
        REFERENCES connection (id, provider_id) ON DELETE CASCADE,
    CONSTRAINT ck_stub_connection_provider_id CHECK (provider_id = 99),
    CONSTRAINT uk_stub_connection_address UNIQUE (address)
);
