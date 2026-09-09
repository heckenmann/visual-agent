CREATE TABLE main_agent_long_term_memory (
    scope TEXT PRIMARY KEY NOT NULL,
    content TEXT NOT NULL,
    content_length INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO main_agent_long_term_memory (scope, content, content_length, revision, updated_at)
VALUES ('main', '', 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));
