CREATE TABLE IF NOT EXISTS user_preferences (
    preference_key VARCHAR(512) PRIMARY KEY,
    preference_value VARCHAR(1000000) NOT NULL,
    preference_type VARCHAR(64) NOT NULL DEFAULT 'string',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
