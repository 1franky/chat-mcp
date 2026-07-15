package com.aidatachat.adapters.out.persistence.identity;

import com.aidatachat.application.port.out.UserAccountRepository;
import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserPage;
import com.aidatachat.domain.model.UserRole;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountJpaAdapter implements UserAccountRepository {

    private static final long BOOTSTRAP_LOCK_ID = 4_210_001L;
    private static final long ADMINISTRATION_LOCK_ID = 4_210_002L;

    private final SpringDataUserAccountRepository repository;
    private final EntityManager entityManager;

    public UserAccountJpaAdapter(
            SpringDataUserAccountRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void acquireBootstrapLock() {
        acquireTransactionLock(BOOTSTRAP_LOCK_ID);
    }

    @Override
    public void acquireAdministrationLock() {
        acquireTransactionLock(ADMINISTRATION_LOCK_ID);
    }

    @Override
    public boolean existsAny() {
        return repository.count() > 0;
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return repository.findById(id).map(UserAccountEntity::toDomain);
    }

    @Override
    public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
        return repository.findByNormalizedEmail(normalizedEmail).map(UserAccountEntity::toDomain);
    }

    @Override
    public UserAccount save(UserAccount account) {
        UserAccountEntity entity =
                repository
                        .findById(account.id())
                        .map(
                                existing -> {
                                    existing.update(account);
                                    return existing;
                                })
                        .orElseGet(() -> new UserAccountEntity(account));
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public long countActiveAdministrators() {
        return repository.countByActiveTrueAndRole(UserRole.ADMIN);
    }

    @Override
    public UserPage findAll(int page, int size) {
        var result =
                repository.findAll(
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new UserPage(
                result.getContent().stream().map(UserAccountEntity::toDomain).toList(),
                result.getTotalElements(),
                page,
                size);
    }

    private void acquireTransactionLock(long lockId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:lockId)", Object.class)
                .setParameter("lockId", lockId)
                .getSingleResult();
    }
}
