-- V15 predates the distinction between bootstrap rows and meaningful user state.
-- Reclassify only its newly introduced state from persistent user evidence.
UPDATE user_preferences
SET value = CASE
        WHEN EXISTS (SELECT 1 FROM user_preferences WHERE key <> 'ui.onboarding.v1')
          OR EXISTS (SELECT 1 FROM conversation_history)
          OR EXISTS (SELECT 1 FROM todos)
          OR EXISTS (SELECT 1 FROM sub_agents)
          OR EXISTS (SELECT 1 FROM workspace_files)
          OR EXISTS (SELECT 1 FROM main_agent_long_term_memory WHERE content_length > 0)
          OR EXISTS (SELECT 1 FROM skills) THEN 'COMPLETED'
        ELSE 'NOT_STARTED'
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE key = 'ui.onboarding.v1'
  AND value IN ('COMPLETED', 'NOT_STARTED');
