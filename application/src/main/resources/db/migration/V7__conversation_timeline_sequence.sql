CREATE TABLE conversation_timeline_sequence (
    value INTEGER PRIMARY KEY AUTOINCREMENT
);

ALTER TABLE conversation_history ADD COLUMN timeline_sequence INTEGER NOT NULL DEFAULT 0;
ALTER TABLE todos ADD COLUMN timeline_sequence INTEGER NOT NULL DEFAULT 0;
ALTER TABLE deleted_todos ADD COLUMN timeline_sequence INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_conversation_history_timeline_sequence
    ON conversation_history (timeline_sequence DESC, id DESC);

CREATE INDEX idx_todos_timeline_sequence
    ON todos (timeline_sequence DESC, id DESC);

CREATE INDEX idx_deleted_todos_timeline_sequence
    ON deleted_todos (timeline_sequence DESC, id DESC);
