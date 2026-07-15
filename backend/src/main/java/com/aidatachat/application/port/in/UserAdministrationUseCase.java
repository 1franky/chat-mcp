package com.aidatachat.application.port.in;

import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserPage;
import com.aidatachat.domain.model.UserRole;
import java.util.UUID;

public interface UserAdministrationUseCase {

    UserPage listUsers(int page, int size);

    UserAccount createUser(UUID actorId, CreateUserCommand command);

    UserAccount changeRole(UUID actorId, UUID targetId, UserRole newRole, String remoteAddress);

    UserAccount deactivateUser(UUID actorId, UUID targetId, String remoteAddress);

    record CreateUserCommand(
            String email, String displayName, String rawPassword, String remoteAddress) {}
}
