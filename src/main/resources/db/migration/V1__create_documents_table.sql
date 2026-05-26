CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255)  NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    file_size     BIGINT        NOT NULL,
    file_path     VARCHAR(500)  NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    uploaded_at   TIMESTAMP     NOT NULL,
    processed_at  TIMESTAMP
);
