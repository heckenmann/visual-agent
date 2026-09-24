ALTER TABLE conversation_history
    ADD COLUMN parent_assistant_turn_id VARCHAR(255);

ALTER TABLE conversation_history
    ADD COLUMN turn_order INTEGER;

ALTER TABLE conversation_history
    ADD COLUMN assistant_tool_turn BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE conversation_history
    ADD COLUMN conversation_request_id VARCHAR(255);

ALTER TABLE conversation_history
    ADD CONSTRAINT fk_conversation_tool_parent
    FOREIGN KEY (parent_assistant_turn_id) REFERENCES conversation_history (id) ON DELETE CASCADE;

CREATE INDEX idx_conversation_history_parent_order
    ON conversation_history (parent_assistant_turn_id, turn_order, timeline_sequence);

CREATE INDEX idx_conversation_history_request_turn
    ON conversation_history (conversation_request_id, timeline_sequence);
