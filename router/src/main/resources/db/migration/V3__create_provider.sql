-- Catalog of messenger platforms a user can be notified through.
--
-- No rows here: each provider's own migration inserts its row with a fixed id, which its
-- ConnectionProvider declares, so the id is not an identity column.
--
-- This table deliberately holds no destination: a platform is not somewhere to send a
-- message. A user's destination is a connection row plus that provider's own *_connection
-- details row.
--
-- link_instructions is what the user is told to do after starting a link. The core replaces
-- {token} with the session's one-time token, and the CHECK keeps the placeholder from being
-- forgotten.

CREATE TABLE provider (
    id                BIGINT       NOT NULL,
    title             VARCHAR(255) NOT NULL,
    link_instructions TEXT         NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT ck_provider_link_instructions_token CHECK (link_instructions LIKE '%{token}%')
);
