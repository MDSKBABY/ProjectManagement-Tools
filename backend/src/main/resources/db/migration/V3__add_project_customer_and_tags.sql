ALTER TABLE project
    ADD COLUMN customer_name VARCHAR(200),
    ADD COLUMN tags JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD CONSTRAINT ck_project_tags_array CHECK (jsonb_typeof(tags) = 'array');

CREATE INDEX idx_project_tags ON project USING GIN (tags);
