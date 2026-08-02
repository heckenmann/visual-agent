-- Adds position support to todos and seeds existing rows from creation order.
-- Because SQLite versions vary, priority is left unused instead of dropped.

ALTER TABLE todos ADD COLUMN position INTEGER DEFAULT 0;

UPDATE todos
SET position = (
    SELECT COUNT(*) - 1
    FROM todos other
    WHERE other.created_at < todos.created_at
       OR (other.created_at = todos.created_at AND other.id < todos.id)
)
WHERE position = 0;
