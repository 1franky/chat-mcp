package com.aidatachat.adapters.out.persistence.audit;

import com.aidatachat.application.port.out.AuditRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "security_audit_event", schema = "audit")
class SecurityAuditEventEntity {

    @Id private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "remote_address", length = 64)
    private String remoteAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safe_metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> safeMetadata;

    protected SecurityAuditEventEntity() {}

    SecurityAuditEventEntity(AuditRepository.AuditEvent event) {
        this.id = UUID.randomUUID();
        this.actorUserId = event.actorId();
        this.targetUserId = event.targetId();
        this.eventType = event.eventType();
        this.success = event.success();
        this.occurredAt = event.occurredAt();
        this.remoteAddress = event.remoteAddress();
        this.safeMetadata = Map.copyOf(event.safeMetadata());
    }
}
