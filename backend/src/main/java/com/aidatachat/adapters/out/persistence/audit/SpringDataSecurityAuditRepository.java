package com.aidatachat.adapters.out.persistence.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSecurityAuditRepository extends JpaRepository<SecurityAuditEventEntity, UUID> {}
