CREATE TABLE file_upload_chunk (
    file_asset_id BIGINT NOT NULL REFERENCES file_asset (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    size_bytes INTEGER NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_asset_id, chunk_index),
    CONSTRAINT ck_file_upload_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_file_upload_chunk_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_file_upload_chunk_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_file_upload_chunk_asset
    ON file_upload_chunk (file_asset_id, chunk_index);
