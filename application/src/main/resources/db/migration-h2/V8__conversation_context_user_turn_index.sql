CREATE INDEX idx_conversation_history_session_role
    ON conversation_history (session_id, role, timeline_sequence DESC, created_at DESC, id DESC);

CREATE INDEX idx_conversation_history_session_timeline
    ON conversation_history (session_id, timeline_sequence DESC, created_at DESC, id DESC);

CREATE INDEX idx_workspace_files_imported_id
    ON workspace_files (imported_at DESC, id DESC);

DROP INDEX idx_workspace_files_imported;

CREATE INDEX idx_sub_agents_status_created
    ON sub_agents (status, created_at DESC, id DESC);
