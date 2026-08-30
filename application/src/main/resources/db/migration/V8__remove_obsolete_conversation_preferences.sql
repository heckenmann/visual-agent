DELETE FROM user_preferences
WHERE key IN (
    'session.streaming.enabled',
    'session.thinking.enabled',
    'session.auto.compaction.enabled'
);
