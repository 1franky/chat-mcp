package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserPage;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    void acquireBootstrapLock();

    void acquireAdministrationLock();

    boolean existsAny();

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

    UserAccount save(UserAccount account);

    long countActiveAdministrators();

    UserPage findAll(int page, int size);
}
