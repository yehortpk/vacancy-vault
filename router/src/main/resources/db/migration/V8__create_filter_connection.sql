-- Binds a filter to the connections it notifies. A filter with no row here is a draft. Both
-- foreign keys cascade, so unlinking a connection or deleting a filter cleans up on its own.
CREATE TABLE filter_connection (
    filter_id     BIGINT NOT NULL REFERENCES filter (id) ON DELETE CASCADE,
    connection_id BIGINT NOT NULL REFERENCES connection (id) ON DELETE CASCADE,
    PRIMARY KEY (filter_id, connection_id)
);

CREATE INDEX idx_filter_connection_connection_id ON filter_connection (connection_id);
