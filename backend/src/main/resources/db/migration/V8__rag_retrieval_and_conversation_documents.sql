CREATE TABLE chat.conversation_document (
    conversation_id uuid NOT NULL REFERENCES chat.conversation (id) ON DELETE CASCADE,
    document_id uuid NOT NULL REFERENCES rag.document (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (conversation_id, document_id)
);

CREATE INDEX conversation_document_document_idx
    ON chat.conversation_document (document_id);

-- V7 declared chunk_id ON DELETE SET NULL, but message_document_chunk_required_check requires
-- chunk_id IS NOT NULL whenever relation='CITED': deleting a cited chunk would violate the
-- table's own CHECK constraint. Nothing populated CITED rows before this migration, so this was
-- latent, not yet triggered.
ALTER TABLE rag.message_document
    DROP CONSTRAINT message_document_chunk_id_fkey,
    ADD CONSTRAINT message_document_chunk_id_fkey
        FOREIGN KEY (chunk_id) REFERENCES rag.document_chunk (id) ON DELETE CASCADE;

-- Similarity score at retrieval time, only meaningful for relation='CITED' — document_chunk has
-- no notion of "score" on its own (it is a property of a query, not of the chunk), so it has to
-- be captured here to make citations reproducible when re-reading a conversation's history.
ALTER TABLE rag.message_document
    ADD COLUMN score double precision,
    ADD CONSTRAINT message_document_score_check CHECK (
        (relation = 'CITED' AND score IS NOT NULL)
        OR (relation = 'SELECTED' AND score IS NULL)
    );
