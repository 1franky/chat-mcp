CREATE TABLE chat.message_tool_call (
    id uuid PRIMARY KEY,
    message_id uuid NOT NULL REFERENCES chat.message (id) ON DELETE CASCADE,
    generation_round smallint NOT NULL,
    sequence smallint NOT NULL,
    tool_name varchar(200) NOT NULL,
    provider_tool_call_id varchar(200),
    arguments jsonb NOT NULL,
    status varchar(16) NOT NULL,
    is_error boolean,
    result jsonb,
    error_code varchar(120),
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT message_tool_call_round_check CHECK (generation_round BETWEEN 1 AND 100),
    CONSTRAINT message_tool_call_sequence_check CHECK (sequence >= 0),
    CONSTRAINT message_tool_call_name_check
        CHECK (length(btrim(tool_name)) BETWEEN 1 AND 200),
    CONSTRAINT message_tool_call_status_check
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'BLOCKED', 'TIMEOUT')),
    CONSTRAINT message_tool_call_terminal_check CHECK (
        (status IN ('PENDING', 'RUNNING') AND completed_at IS NULL)
        OR (status NOT IN ('PENDING', 'RUNNING') AND completed_at IS NOT NULL)
    ),
    UNIQUE (message_id, generation_round, sequence)
);

CREATE INDEX message_tool_call_message_idx
    ON chat.message_tool_call (message_id, generation_round, sequence);
