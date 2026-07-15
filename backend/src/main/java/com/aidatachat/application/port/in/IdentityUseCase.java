package com.aidatachat.application.port.in;

import com.aidatachat.domain.model.UserAccount;
import java.util.UUID;

public interface IdentityUseCase {

    BootstrapStatus bootstrap(UUID authenticatedUserId);

    UserAccount register(RegisterCommand command);

    UserAccount currentUser(UUID userId);

    void recordLoginSuccess(UUID userId, String remoteAddress);

    void recordLoginFailure(String remoteAddress);

    void recordLogout(UUID userId, String remoteAddress);

    record BootstrapStatus(
            boolean bootstrapRequired,
            boolean registrationOpen,
            boolean authenticated,
            UserAccount user) {}

    record RegisterCommand(
            String email, String displayName, String rawPassword, String remoteAddress) {}
}
