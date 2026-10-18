ALTER TABLE todos ADD COLUMN task_contract VARCHAR(32768);
ALTER TABLE deleted_todos ADD COLUMN task_contract VARCHAR(32768);
ALTER TABLE todos ADD COLUMN accepted_result VARCHAR(262144);
ALTER TABLE deleted_todos ADD COLUMN accepted_result VARCHAR(262144);
CREATE TABLE task_attempts (
    execution_id VARCHAR(100) PRIMARY KEY,
    todo_id VARCHAR(100) NOT NULL,
    attempt_number INT NOT NULL,
    created_at VARCHAR(100) NOT NULL,
    result_json VARCHAR(262144) NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX task_attempts_by_todo ON task_attempts(todo_id, created_at, execution_id);
