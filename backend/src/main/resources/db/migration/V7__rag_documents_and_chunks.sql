CREATE TABLE rag.document (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES identity.app_user (id) ON DELETE CASCADE,
    original_filename varchar(255) NOT NULL,
    storage_key varchar(255) NOT NULL,
    mime_type varchar(127) NOT NULL,
    byte_size bigint NOT NULL,
    content_hash varchar(64) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'UPLOADED',
    failure_reason varchar(255),
    embedding_model_id varchar(255),
    embedding_dimension integer,
    chunk_count integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT document_filename_check CHECK (length(btrim(original_filename)) BETWEEN 1 AND 255),
    CONSTRAINT document_storage_key_check CHECK (length(btrim(storage_key)) BETWEEN 1 AND 255),
    CONSTRAINT document_mime_type_check CHECK (length(btrim(mime_type)) BETWEEN 1 AND 127),
    CONSTRAINT document_byte_size_check CHECK (byte_size > 0),
    CONSTRAINT document_content_hash_check CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT document_status_check
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED', 'DELETING')),
    CONSTRAINT document_chunk_count_check CHECK (chunk_count >= 0),
    CONSTRAINT document_embedding_dimension_check
        CHECK (embedding_dimension IS NULL OR embedding_dimension > 0),
    CONSTRAINT document_ready_metadata_check CHECK (
        (status = 'READY' AND embedding_model_id IS NOT NULL AND embedding_dimension IS NOT NULL)
        OR (status <> 'READY')
    )
);

CREATE UNIQUE INDEX document_owner_hash_uq
    ON rag.document (owner_id, content_hash);
CREATE INDEX document_owner_updated_idx
    ON rag.document (owner_id, updated_at DESC, id DESC);
CREATE INDEX document_owner_status_idx
    ON rag.document (owner_id, status);

CREATE TABLE rag.document_chunk (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES rag.document (id) ON DELETE CASCADE,
    owner_id uuid NOT NULL REFERENCES identity.app_user (id) ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    page_number integer,
    section_label varchar(255),
    embedding_model_id varchar(255) NOT NULL,
    embedding vector(1536) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT document_chunk_index_check CHECK (chunk_index >= 0),
    CONSTRAINT document_chunk_content_check CHECK (length(content) BETWEEN 1 AND 8000),
    CONSTRAINT document_chunk_page_number_check CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT document_chunk_section_label_check
        CHECK (section_label IS NULL OR length(btrim(section_label)) BETWEEN 1 AND 255),
    CONSTRAINT document_chunk_embedding_model_check
        CHECK (length(btrim(embedding_model_id)) BETWEEN 1 AND 255),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX document_chunk_owner_idx
    ON rag.document_chunk (owner_id);
CREATE INDEX document_chunk_embedding_hnsw_idx
    ON rag.document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE rag.message_document (
    id uuid PRIMARY KEY,
    message_id uuid NOT NULL REFERENCES chat.message (id) ON DELETE CASCADE,
    document_id uuid NOT NULL REFERENCES rag.document (id) ON DELETE CASCADE,
    chunk_id uuid REFERENCES rag.document_chunk (id) ON DELETE SET NULL,
    relation varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT message_document_relation_check CHECK (relation IN ('SELECTED', 'CITED')),
    CONSTRAINT message_document_chunk_required_check CHECK (
        (relation = 'SELECTED' AND chunk_id IS NULL)
        OR (relation = 'CITED' AND chunk_id IS NOT NULL)
    )
);

CREATE INDEX message_document_document_idx
    ON rag.message_document (document_id);
CREATE UNIQUE INDEX message_document_selection_uq
    ON rag.message_document (message_id, document_id)
    WHERE relation = 'SELECTED';
CREATE UNIQUE INDEX message_document_citation_uq
    ON rag.message_document (message_id, chunk_id)
    WHERE relation = 'CITED';
