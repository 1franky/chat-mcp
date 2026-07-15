package com.aidatachat.adapters.out.persistence.audit;

import com.aidatachat.application.port.out.AuditRepository;
import org.springframework.stereotype.Repository;

@Repository
public class SecurityAuditJpaAdapter implements AuditRepository {

    private final SpringDataSecurityAuditRepository repository;

    public SecurityAuditJpaAdapter(SpringDataSecurityAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(AuditEvent event) {
        repository.save(new SecurityAuditEventEntity(event));
    }
}
