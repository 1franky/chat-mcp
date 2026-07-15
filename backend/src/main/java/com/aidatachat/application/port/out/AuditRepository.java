package com.aidatachat.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AuditRepository {

    void append(AuditEvent event);

    record AuditEvent(
            UUID actorId, String eventType, Instant occurredAt, Map<String, String> safeMetadata) {}
}
