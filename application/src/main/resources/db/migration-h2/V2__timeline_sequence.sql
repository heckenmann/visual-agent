CREATE SEQUENCE IF NOT EXISTS visual_agent_timeline_sequence START WITH 1;

ALTER SEQUENCE visual_agent_timeline_sequence RESTART WITH (
    SELECT COALESCE(MAX(timeline_sequence), 0) + 1
    FROM (
        SELECT timeline_sequence FROM conversation_history
        UNION ALL
        SELECT timeline_sequence FROM todos
        UNION ALL
        SELECT timeline_sequence FROM deleted_todos
    ) timeline_values
);
