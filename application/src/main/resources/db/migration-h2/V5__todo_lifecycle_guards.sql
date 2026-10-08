ALTER TABLE todos ADD COLUMN decomposition_depth INTEGER NOT NULL DEFAULT 0;
ALTER TABLE deleted_todos ADD COLUMN decomposition_depth INTEGER NOT NULL DEFAULT 0;
CREATE TABLE todo_mutation_lock (id INTEGER PRIMARY KEY, revision BIGINT NOT NULL);
INSERT INTO todo_mutation_lock (id, revision) VALUES (1, 0);
