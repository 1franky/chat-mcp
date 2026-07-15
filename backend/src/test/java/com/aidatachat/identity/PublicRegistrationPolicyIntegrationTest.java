package com.aidatachat.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.exception.RegistrationClosedException;
import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.application.port.in.UserAdministrationUseCase;
import com.aidatachat.application.port.in.UserAdministrationUseCase.CreateUserCommand;
import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "app.identity.allow-public-registration=false")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class PublicRegistrationPolicyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_registration_policy_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired private IdentityUseCase identity;
    @Autowired private UserAdministrationUseCase administration;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM audit.security_audit_event");
        jdbc.update("DELETE FROM identity.app_user");
    }

    @Test
    void disabledPublicRegistrationStillAllowsBootstrapAndThenOnlyAdminCreation() {
        UserAccount administrator = register("admin@example.test", "Admin User");

        assertThat(administrator.role()).isEqualTo(UserRole.ADMIN);
        assertThatThrownBy(() -> register("public@example.test", "Public User"))
                .isInstanceOf(RegistrationClosedException.class);

        UserAccount adminCreated =
                administration.createUser(
                        administrator.id(),
                        new CreateUserCommand(
                                "created@example.test",
                                "Created User",
                                "correct-horse-battery-staple",
                                "127.0.0.1"));
        assertThat(adminCreated.role()).isEqualTo(UserRole.USER);
    }

    private UserAccount register(String email, String displayName) {
        return identity.register(
                new RegisterCommand(
                        email, displayName, "correct-horse-battery-staple", "127.0.0.1"));
    }
}
