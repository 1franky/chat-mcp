package com.aidatachat.application.service;

import com.aidatachat.application.exception.DuplicateUserException;
import com.aidatachat.application.exception.LastAdministratorException;
import com.aidatachat.application.exception.RegistrationClosedException;
import com.aidatachat.application.exception.UserNotFoundException;
import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.UserAdministrationUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.IdentityTransactionPort;
import com.aidatachat.application.port.out.PasswordHashPort;
import com.aidatachat.application.port.out.SessionInvalidationPort;
import com.aidatachat.application.port.out.UserAccountRepository;
import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserPage;
import com.aidatachat.domain.model.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class IdentityService implements IdentityUseCase, UserAdministrationUseCase {

    private final UserAccountRepository users;
    private final PasswordHashPort passwordHash;
    private final AuditRepository audit;
    private final IdentityTransactionPort transactions;
    private final SessionInvalidationPort sessions;
    private final Clock clock;
    private final boolean publicRegistrationAllowed;

    public IdentityService(
            UserAccountRepository users,
            PasswordHashPort passwordHash,
            AuditRepository audit,
            IdentityTransactionPort transactions,
            SessionInvalidationPort sessions,
            Clock clock,
            boolean publicRegistrationAllowed) {
        this.users = Objects.requireNonNull(users);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.audit = Objects.requireNonNull(audit);
        this.transactions = Objects.requireNonNull(transactions);
        this.sessions = Objects.requireNonNull(sessions);
        this.clock = Objects.requireNonNull(clock);
        this.publicRegistrationAllowed = publicRegistrationAllowed;
    }

    @Override
    public BootstrapStatus bootstrap(UUID authenticatedUserId) {
        boolean bootstrapRequired = !users.existsAny();
        UserAccount current =
                authenticatedUserId == null ? null : findActiveUser(authenticatedUserId);
        return new BootstrapStatus(
                bootstrapRequired,
                bootstrapRequired || publicRegistrationAllowed,
                current != null,
                current);
    }

    @Override
    public UserAccount register(RegisterCommand command) {
        Objects.requireNonNull(command);
        ValidatedIdentity identity =
                validate(command.email(), command.displayName(), command.rawPassword());
        String encodedPassword = passwordHash.hash(command.rawPassword());
        return transactions.serializable(
                () -> {
                    users.acquireBootstrapLock();
                    boolean firstAccount = !users.existsAny();
                    if (!firstAccount && !publicRegistrationAllowed) {
                        throw new RegistrationClosedException();
                    }
                    ensureEmailAvailable(identity.normalizedEmail());
                    UserAccount account =
                            UserAccount.create(
                                    identity.email(),
                                    identity.normalizedEmail(),
                                    identity.displayName(),
                                    encodedPassword,
                                    firstAccount ? UserRole.ADMIN : UserRole.USER,
                                    clock.instant());
                    UserAccount saved = users.save(account);
                    appendAudit(
                            saved.id(),
                            saved.id(),
                            "USER_REGISTERED",
                            true,
                            command.remoteAddress(),
                            Map.of("assigned_role", saved.role().name()));
                    return saved;
                });
    }

    @Override
    public UserAccount currentUser(UUID userId) {
        UserAccount account = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!account.active()) {
            throw new UserNotFoundException();
        }
        return account;
    }

    @Override
    public void recordLoginSuccess(UUID userId, String remoteAddress) {
        appendAudit(userId, userId, "LOGIN_SUCCEEDED", true, remoteAddress, Map.of());
    }

    @Override
    public void recordLoginFailure(String remoteAddress) {
        appendAudit(null, null, "LOGIN_FAILED", false, remoteAddress, Map.of());
    }

    @Override
    public void recordLogout(UUID userId, String remoteAddress) {
        appendAudit(userId, userId, "LOGOUT", true, remoteAddress, Map.of());
    }

    @Override
    public UserPage listUsers(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid page request");
        }
        return users.findAll(page, size);
    }

    @Override
    public UserAccount createUser(UUID actorId, CreateUserCommand command) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(command);
        ValidatedIdentity identity =
                validate(command.email(), command.displayName(), command.rawPassword());
        String encodedPassword = passwordHash.hash(command.rawPassword());
        return transactions.serializable(
                () -> {
                    ensureActiveAdministrator(actorId);
                    ensureEmailAvailable(identity.normalizedEmail());
                    UserAccount saved =
                            users.save(
                                    UserAccount.create(
                                            identity.email(),
                                            identity.normalizedEmail(),
                                            identity.displayName(),
                                            encodedPassword,
                                            UserRole.USER,
                                            clock.instant()));
                    appendAudit(
                            actorId,
                            saved.id(),
                            "USER_CREATED_BY_ADMIN",
                            true,
                            command.remoteAddress(),
                            Map.of("assigned_role", UserRole.USER.name()));
                    return saved;
                });
    }

    @Override
    public UserAccount changeRole(
            UUID actorId, UUID targetId, UserRole newRole, String remoteAddress) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(newRole);
        UserAccount updated =
                transactions.serializable(
                        () -> {
                            users.acquireAdministrationLock();
                            ensureActiveAdministrator(actorId);
                            UserAccount target = currentUser(targetId);
                            if (target.role() == UserRole.ADMIN
                                    && newRole == UserRole.USER
                                    && users.countActiveAdministrators() <= 1) {
                                throw new LastAdministratorException();
                            }
                            if (target.role() == newRole) {
                                return target;
                            }
                            UserAccount saved =
                                    users.save(target.withRole(newRole, clock.instant()));
                            appendAudit(
                                    actorId,
                                    targetId,
                                    "USER_ROLE_CHANGED",
                                    true,
                                    remoteAddress,
                                    Map.of(
                                            "previous_role",
                                            target.role().name(),
                                            "new_role",
                                            newRole.name()));
                            return saved;
                        });
        sessions.invalidateByPrincipalName(updated.normalizedEmail());
        return updated;
    }

    @Override
    public UserAccount deactivateUser(UUID actorId, UUID targetId, String remoteAddress) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(targetId);
        UserAccount deactivated =
                transactions.serializable(
                        () -> {
                            users.acquireAdministrationLock();
                            ensureActiveAdministrator(actorId);
                            UserAccount target = currentUser(targetId);
                            if (target.role() == UserRole.ADMIN
                                    && users.countActiveAdministrators() <= 1) {
                                throw new LastAdministratorException();
                            }
                            UserAccount saved = users.save(target.deactivate(clock.instant()));
                            appendAudit(
                                    actorId,
                                    targetId,
                                    "USER_DEACTIVATED",
                                    true,
                                    remoteAddress,
                                    Map.of("previous_role", target.role().name()));
                            return saved;
                        });
        sessions.invalidateByPrincipalName(deactivated.normalizedEmail());
        return deactivated;
    }

    private void ensureEmailAvailable(String normalizedEmail) {
        if (users.findByNormalizedEmail(normalizedEmail).isPresent()) {
            throw new DuplicateUserException();
        }
    }

    private void ensureActiveAdministrator(UUID actorId) {
        UserAccount actor = currentUser(actorId);
        if (actor.role() != UserRole.ADMIN) {
            throw new UserNotFoundException();
        }
    }

    private UserAccount findActiveUser(UUID userId) {
        return users.findById(userId).filter(UserAccount::active).orElse(null);
    }

    private ValidatedIdentity validate(String email, String displayName, String rawPassword) {
        String cleanEmail = Objects.requireNonNull(email, "email is required").trim();
        String normalizedEmail = cleanEmail.toLowerCase(Locale.ROOT);
        String cleanName = Objects.requireNonNull(displayName, "displayName is required").trim();
        Objects.requireNonNull(rawPassword, "password is required");
        if (cleanEmail.isBlank() || cleanEmail.length() > 320 || !cleanEmail.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        if (cleanName.length() < 2 || cleanName.length() > 120) {
            throw new IllegalArgumentException("Invalid display name");
        }
        if (rawPassword.length() < 12 || rawPassword.length() > 128) {
            throw new IllegalArgumentException("Invalid password length");
        }
        return new ValidatedIdentity(cleanEmail, normalizedEmail, cleanName);
    }

    private void appendAudit(
            UUID actorId,
            UUID targetId,
            String eventType,
            boolean success,
            String remoteAddress,
            Map<String, String> metadata) {
        Instant occurredAt = clock.instant();
        audit.append(
                new AuditRepository.AuditEvent(
                        actorId,
                        targetId,
                        eventType,
                        success,
                        occurredAt,
                        remoteAddress,
                        Map.copyOf(metadata)));
    }

    private record ValidatedIdentity(String email, String normalizedEmail, String displayName) {}
}
