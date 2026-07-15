CREATE TABLE identity.app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT app_user_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT app_user_normalized_email_lowercase CHECK (
        normalized_email = lower(btrim(normalized_email))
    ),
    CONSTRAINT app_user_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT app_user_role_valid CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT app_user_normalized_email_unique UNIQUE (normalized_email)
);

CREATE INDEX app_user_role_active_idx ON identity.app_user (role, active);
CREATE INDEX app_user_created_at_idx ON identity.app_user (created_at DESC, id);

CREATE TABLE audit.security_audit_event (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    target_user_id UUID,
    event_type VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    remote_address VARCHAR(64),
    safe_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT security_audit_event_type_not_blank CHECK (btrim(event_type) <> '')
);

CREATE INDEX security_audit_event_occurred_at_idx
    ON audit.security_audit_event (occurred_at DESC, id);
CREATE INDEX security_audit_event_actor_idx
    ON audit.security_audit_event (actor_user_id, occurred_at DESC);
CREATE INDEX security_audit_event_target_idx
    ON audit.security_audit_event (target_user_id, occurred_at DESC);

CREATE TABLE identity.spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(320),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON identity.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON identity.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON identity.spring_session (principal_name);

CREATE TABLE identity.spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES identity.spring_session(primary_id) ON DELETE CASCADE
);
