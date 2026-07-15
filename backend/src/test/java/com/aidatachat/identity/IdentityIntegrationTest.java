package com.aidatachat.identity;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.exception.LastAdministratorException;
import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.application.port.in.UserAdministrationUseCase;
import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserRole;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class IdentityIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_identity_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired private IdentityUseCase identity;
    @Autowired private UserAdministrationUseCase administration;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM identity.spring_session_attributes");
        jdbc.update("DELETE FROM identity.spring_session");
        jdbc.update("DELETE FROM audit.security_audit_event");
        jdbc.update("DELETE FROM identity.app_user");
        mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext)
                        .apply(springSecurity())
                        .build();
    }

    @Test
    void firstUserBecomesAdministratorAndPasswordUsesArgon2id() {
        UserAccount first = register("first@example.test", "First Admin");

        assertThat(first.role()).isEqualTo(UserRole.ADMIN);
        assertThat(first.passwordHash()).startsWith("$argon2id$");
    }

    @Test
    void laterUsersAreRegularUsers() {
        register("admin@example.test", "Admin User");

        UserAccount second = register("second@example.test", "Second User");

        assertThat(second.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void concurrentBootstrapCreatesExactlyOneAdministrator() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<UserAccount> first =
                    executor.submit(
                            () -> concurrentRegister("one@example.test", "User One", ready, start));
            Future<UserAccount> second =
                    executor.submit(
                            () -> concurrentRegister("two@example.test", "User Two", ready, start));
            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();

            List<UserRole> roles =
                    List.of(first.get(60, SECONDS).role(), second.get(60, SECONDS).role());

            assertThat(roles).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.USER);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM identity.app_user WHERE role = 'ADMIN'",
                                    Long.class))
                    .isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void regularUserReceivesForbiddenFromAdministrativeEndpoints() throws Exception {
        register("admin@example.test", "Admin User");
        UserAccount regular = register("regular@example.test", "Regular User");

        mockMvc.perform(get("/api/admin/users").with(user(principal(regular))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ai-data-chat:problem:forbidden"));
    }

    @Test
    void lastAdministratorCannotBeDemotedOrDeactivated() {
        UserAccount administrator = register("admin@example.test", "Admin User");

        assertThatThrownBy(
                        () ->
                                administration.changeRole(
                                        administrator.id(),
                                        administrator.id(),
                                        UserRole.USER,
                                        "127.0.0.1"))
                .isInstanceOf(LastAdministratorException.class);
        assertThatThrownBy(
                        () ->
                                administration.deactivateUser(
                                        administrator.id(), administrator.id(), "127.0.0.1"))
                .isInstanceOf(LastAdministratorException.class);
    }

    @Test
    void registrationResponseNeverReturnsPasswordHash() throws Exception {
        mockMvc.perform(
                        post("/api/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "api@example.test",
                                          "displayName": "API Admin",
                                          "password": "correct-horse-battery-staple"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void csrfIsRequiredForRegistration() throws Exception {
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "api@example.test",
                                          "displayName": "API Admin",
                                          "password": "correct-horse-battery-staple"
                                        }
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginFailuresUseTheSameGenericMessage() throws Exception {
        register("admin@example.test", "Admin User");
        String expectedDetail = "El correo o la contrasena no son validos.";

        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "admin@example.test",
                                          "password": "incorrect-password"
                                        }
                                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(expectedDetail));
        mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "missing@example.test",
                                          "password": "incorrect-password"
                                        }
                                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(expectedDetail));
    }

    @Test
    void administrativeCreationCannotEscalateRoleThroughRequestBody() throws Exception {
        UserAccount administrator = register("admin@example.test", "Admin User");

        mockMvc.perform(
                        post("/api/admin/users")
                                .with(user(principal(administrator)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "created@example.test",
                                          "displayName": "Created User",
                                          "password": "correct-horse-battery-staple",
                                          "role": "ADMIN"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    private UserAccount register(String email, String displayName) {
        return identity.register(
                new RegisterCommand(
                        email, displayName, "correct-horse-battery-staple", "127.0.0.1"));
    }

    private UserAccount concurrentRegister(
            String email, String displayName, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, SECONDS)) {
            throw new IllegalStateException("Concurrent registration did not start");
        }
        return register(email, displayName);
    }

    private AuthenticatedUser principal(UserAccount account) {
        return new AuthenticatedUser(
                account.id(),
                account.email(),
                account.normalizedEmail(),
                account.displayName(),
                account.passwordHash(),
                account.role(),
                account.active());
    }
}
