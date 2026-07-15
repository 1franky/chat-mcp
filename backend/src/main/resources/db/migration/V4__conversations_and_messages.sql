CREATE TABLE chat.conversation (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES identity.app_user (id) ON DELETE CASCADE,
    title varchar(160) NOT NULL,
    provider_connection_id uuid REFERENCES chat.provider_connection (id) ON DELETE SET NULL,
    model_id varchar(255) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT conversation_title_check CHECK (length(btrim(title)) BETWEEN 1 AND 160),
    CONSTRAINT conversation_model_check CHECK (length(btrim(model_id)) BETWEEN 1 AND 255)
);

CREATE INDEX conversation_owner_updated_idx
    ON chat.conversation (owner_id, updated_at DESC, id DESC);
CREATE INDEX conversation_owner_title_idx
    ON chat.conversation (owner_id, lower(title));

CREATE TABLE chat.message (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES chat.conversation (id) ON DELETE CASCADE,
    position bigint NOT NULL,
    role varchar(16) NOT NULL,
    content text NOT NULL,
    provider_connection_id uuid,
    provider_type varchar(32),
    model_id varchar(255),
    status varchar(16) NOT NULL,
    input_tokens integer,
    output_tokens integer,
    finish_reason varchar(120),
    provider_request_id varchar(200),
    regenerated_from_message_id uuid REFERENCES chat.message (id) ON DELETE SET NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT message_position_check CHECK (position > 0),
    CONSTRAINT message_role_check CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT message_status_check
        CHECK (status IN ('STREAMING', 'COMPLETED', 'CANCELLED', 'FAILED')),
    CONSTRAINT message_provider_type_check
        CHECK (provider_type IS NULL OR provider_type IN
            ('OPENAI', 'ANTHROPIC', 'BYTEPLUS', 'OPENAI_COMPATIBLE', 'OLLAMA', 'FAKE')),
    CONSTRAINT message_tokens_check
        CHECK ((input_tokens IS NULL OR input_tokens >= 0)
            AND (output_tokens IS NULL OR output_tokens >= 0)),
    CONSTRAINT message_role_metadata_check CHECK (
        (role = 'USER' AND provider_connection_id IS NULL AND provider_type IS NULL
            AND model_id IS NULL AND status = 'COMPLETED')
        OR
        (role = 'ASSISTANT' AND provider_connection_id IS NOT NULL
            AND provider_type IS NOT NULL AND model_id IS NOT NULL)
    ),
    UNIQUE (conversation_id, position)
);

CREATE INDEX message_conversation_position_idx
    ON chat.message (conversation_id, position, id);
CREATE INDEX message_provider_snapshot_idx
    ON chat.message (provider_connection_id, model_id)
    WHERE role = 'ASSISTANT';
CREATE UNIQUE INDEX message_one_active_generation_uq
    ON chat.message (conversation_id)
    WHERE role = 'ASSISTANT' AND status = 'STREAMING';
