CREATE TABLE conversation_sessions (
    session_id VARCHAR(255) PRIMARY KEY
);

CREATE TABLE conversation_requests (
    request_id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL REFERENCES conversation_sessions(session_id),
    invalidated BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO conversation_sessions (session_id)
SELECT DISTINCT session_id FROM conversation_history;

INSERT INTO conversation_requests (request_id, session_id)
SELECT DISTINCT conversation_request_id, session_id FROM conversation_history
WHERE conversation_request_id IS NOT NULL;

CREATE INDEX idx_conversation_requests_session ON conversation_requests(session_id, invalidated);
