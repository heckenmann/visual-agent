INSERT INTO user_preferences (key, value, updated_at)
SELECT
    'ui.onboarding.v1',
    CASE
        WHEN EXISTS (SELECT 1 FROM user_preferences) THEN 'COMPLETED'
        ELSE 'NOT_STARTED'
    END,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM user_preferences WHERE key = 'ui.onboarding.v1'
);
