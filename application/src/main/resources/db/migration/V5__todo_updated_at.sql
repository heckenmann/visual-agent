-- Tracks the last todo mutation independently from its creation time.
ALTER TABLE todos ADD COLUMN updated_at TIMESTAMP;

UPDATE todos
SET updated_at = created_at
WHERE updated_at IS NULL;
