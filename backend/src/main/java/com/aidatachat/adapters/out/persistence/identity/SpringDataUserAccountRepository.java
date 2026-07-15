package com.aidatachat.adapters.out.persistence.identity;

import com.aidatachat.domain.model.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    Optional<UserAccountEntity> findByNormalizedEmail(String normalizedEmail);

    long countByActiveTrueAndRole(UserRole role);
}
