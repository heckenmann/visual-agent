ALTER TABLE conversation_history
    ADD COLUMN context_policy TEXT NOT NULL DEFAULT 'SUMMARY_SOURCE';

UPDATE conversation_history
SET context_policy = 'DIALOGUE'
WHERE role IN ('user', 'assistant');

CREATE INDEX idx_conversation_history_context
    ON conversation_history (session_id, context_policy, timeline_sequence DESC);
