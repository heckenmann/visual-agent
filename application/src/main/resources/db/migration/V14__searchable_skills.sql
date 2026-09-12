CREATE TABLE skills (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    content_fingerprint TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
    read_count INTEGER NOT NULL DEFAULT 0 CHECK (read_count >= 0),
    last_read_at TIMESTAMP,
    CHECK (length(trim(title)) BETWEEN 1 AND 200),
    CHECK (length(trim(content)) BETWEEN 1 AND 120000)
);

CREATE INDEX idx_skills_updated_at ON skills (updated_at DESC, id ASC);

CREATE TABLE deleted_skill_audit (
    skill_id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    revision INTEGER NOT NULL,
    deleted_at TIMESTAMP NOT NULL
);

CREATE VIRTUAL TABLE skills_fts USING fts5(
    id UNINDEXED,
    title,
    content,
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TRIGGER skills_ai
AFTER INSERT ON skills
BEGIN
    INSERT INTO skills_fts(id, title, content)
    VALUES (new.id, new.title, new.content);
END;

CREATE TRIGGER skills_ad
AFTER DELETE ON skills
BEGIN
    DELETE FROM skills_fts WHERE id = old.id;
END;

CREATE TRIGGER skills_au
AFTER UPDATE OF title, content ON skills
BEGIN
    DELETE FROM skills_fts WHERE id = old.id;
    INSERT INTO skills_fts(id, title, content)
    VALUES (new.id, new.title, new.content);
END;
