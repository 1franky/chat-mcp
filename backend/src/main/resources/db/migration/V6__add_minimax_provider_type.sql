ALTER TABLE chat.provider_connection
    DROP CONSTRAINT provider_connection_type_check;
ALTER TABLE chat.provider_connection
    ADD CONSTRAINT provider_connection_type_check
        CHECK (provider_type IN
            ('OPENAI', 'ANTHROPIC', 'BYTEPLUS', 'MINIMAX', 'OPENAI_COMPATIBLE', 'OLLAMA', 'FAKE'));

ALTER TABLE chat.message
    DROP CONSTRAINT message_provider_type_check;
ALTER TABLE chat.message
    ADD CONSTRAINT message_provider_type_check
        CHECK (provider_type IS NULL OR provider_type IN
            ('OPENAI', 'ANTHROPIC', 'BYTEPLUS', 'MINIMAX', 'OPENAI_COMPATIBLE', 'OLLAMA', 'FAKE'));
