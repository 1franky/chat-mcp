CREATE TABLE chat.provider_connection (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES identity.app_user (id) ON DELETE CASCADE,
    display_name varchar(120) NOT NULL,
    provider_type varchar(32) NOT NULL,
    base_url varchar(2048),
    region varchar(64),
    models_path varchar(255),
    responses_path varchar(255),
    chat_completions_path varchar(255),
    configured_model_id varchar(255),
    default_model_id varchar(255),
    credential_ciphertext bytea,
    credential_nonce bytea,
    credential_key_version integer,
    credential_masked varchar(32),
    state varchar(16) NOT NULL DEFAULT 'NOT_TESTED',
    last_error_code varchar(64),
    last_tested_at timestamptz,
    last_models_synced_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT provider_connection_type_check
        CHECK (provider_type IN ('OPENAI', 'ANTHROPIC', 'BYTEPLUS', 'OPENAI_COMPATIBLE', 'OLLAMA', 'FAKE')),
    CONSTRAINT provider_connection_state_check
        CHECK (state IN ('NOT_TESTED', 'UP', 'DOWN')),
    CONSTRAINT provider_connection_credential_check
        CHECK (
            (credential_ciphertext IS NULL AND credential_nonce IS NULL
                AND credential_key_version IS NULL AND credential_masked IS NULL)
            OR
            (credential_ciphertext IS NOT NULL AND credential_nonce IS NOT NULL
                AND credential_key_version > 0 AND credential_masked IS NOT NULL)
        ),
    CONSTRAINT provider_connection_display_name_check CHECK (length(btrim(display_name)) >= 2)
);

CREATE UNIQUE INDEX provider_connection_owner_name_uq
    ON chat.provider_connection (owner_id, lower(display_name));
CREATE INDEX provider_connection_owner_updated_idx
    ON chat.provider_connection (owner_id, updated_at DESC, id);

CREATE TABLE chat.provider_model (
    id uuid PRIMARY KEY,
    provider_connection_id uuid NOT NULL
        REFERENCES chat.provider_connection (id) ON DELETE CASCADE,
    model_id varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    origin varchar(16) NOT NULL,
    chat_capability varchar(16) NOT NULL,
    streaming_capability varchar(16) NOT NULL,
    tool_calling_capability varchar(16) NOT NULL,
    structured_output_capability varchar(16) NOT NULL,
    vision_capability varchar(16) NOT NULL,
    embeddings_capability varchar(16) NOT NULL,
    model_discovery_capability varchar(16) NOT NULL,
    discovered_at timestamptz NOT NULL,
    last_validated_at timestamptz,
    CONSTRAINT provider_model_origin_check
        CHECK (origin IN ('DISCOVERED', 'MANUAL', 'CONFIGURED')),
    CONSTRAINT provider_model_chat_capability_check
        CHECK (chat_capability IN ('SUPPORTED', 'UNSUPPORTED', 'UNKNOWN')),
    CONSTRAINT provider_model_streaming_capability_check
        CHECK (streaming_capability IN ('SUPPORTED', 'UNSUPPORTED', 'UNKNOWN')),
    CONSTRAINT provider_model_tool_calling_capability_check
        CHECK (tool_calling_capability IN ('SUPPORTED', 'UNSUPPORTED', 'UNKNOWN')),
    CONSTRAINT provider_model_structured_output_capability_check
        CHECK (structured_output_capability IN ('SUPPORTED', 'UNSUPPORTED', 'UNKNOWN')),
    CONSTRAINT provider_model_vision_capability_check
        CHECK (vision_capability IN ('SUPPORTED', 'UNSUPPORTED', 'UNKNOWN')),
    CONSTRAINT provider_model_embeddings_capability_check
        CHECK (embeddings_capability IN ('SUPPORTED', 'UNSUPPORTED', 'UNKNOWN')),
    CONSTRAINT provider_model_discovery_capability_check
        CHECK (model_discovery_capability IN ('SUPPORTED', 'UNSUPPORTED', 'UNKNOWN')),
    UNIQUE (provider_connection_id, model_id)
);

CREATE INDEX provider_model_connection_origin_idx
    ON chat.provider_model (provider_connection_id, origin, model_id);
