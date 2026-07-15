package com.aidatachat.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.exception.ProviderNotFoundException;
import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.application.port.in.ProviderManagementUseCase;
import com.aidatachat.application.port.in.ProviderManagementUseCase.ProviderConnectionView;
import com.aidatachat.application.port.in.ProviderManagementUseCase.SaveProviderCommand;
import com.aidatachat.domain.model.ProviderType;
import com.aidatachat.domain.model.UserAccount;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

@SpringBootTest(
        properties =
                "app.providers.credential-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class ProviderManagementIntegrationTest {

    private static final String RAW_API_KEY = "openai-provider-test-secret";

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_provider_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired private IdentityUseCase identity;
    @Autowired private ProviderManagementUseCase providers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM chat.provider_model");
        jdbc.update("DELETE FROM chat.provider_connection");
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
    void encryptsCredentialsAndNeverReturnsOrAuditsSecrets() throws Exception {
        register("admin@example.test", "Admin");
        UserAccount owner = register("owner@example.test", "Owner");
        UserAccount other = register("other@example.test", "Other");

        ProviderConnectionView created =
                providers.createConnection(
                        owner.id(),
                        command(
                                "OpenAI principal",
                                ProviderType.OPENAI,
                                RAW_API_KEY,
                                null,
                                null,
                                null));

        byte[] ciphertext =
                jdbc.queryForObject(
                        "SELECT credential_ciphertext FROM chat.provider_connection WHERE id = ?",
                        byte[].class,
                        created.id());
        List<String> auditMetadata =
                jdbc.queryForList(
                        "SELECT safe_metadata FROM audit.security_audit_event "
                                + "WHERE event_type LIKE 'PROVIDER_%'",
                        String.class);

        assertThat(ciphertext).isNotNull();
        assertThat(new String(ciphertext, StandardCharsets.UTF_8)).doesNotContain(RAW_API_KEY);
        assertThat(created.credentialMasked()).endsWith("cret").doesNotContain(RAW_API_KEY);
        assertThat(auditMetadata)
                .allSatisfy(value -> assertThat(value).doesNotContain(RAW_API_KEY));

        mockMvc.perform(get("/api/providers").with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].credentialMasked").value("••••cret"))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$[0].credential").doesNotExist())
                .andExpect(jsonPath("$[0].ciphertext").doesNotExist())
                .andExpect(jsonPath("$[0].nonce").doesNotExist());
        mockMvc.perform(get("/api/providers").with(user(principal(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(
                        get("/api/providers/{id}/models", created.id())
                                .with(user(principal(other))))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        delete("/api/providers/{id}", created.id())
                                .with(user(principal(other)))
                                .with(csrf()))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> providers.listModels(other.id(), created.id()))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void fakeProviderSupportsConnectionTestModelSyncAndDefaultSelection() throws Exception {
        UserAccount owner = register("admin@example.test", "Admin");
        ProviderConnectionView created =
                providers.createConnection(
                        owner.id(),
                        command("Fake local", ProviderType.FAKE, null, null, null, null));

        assertThat(providers.testConnection(owner.id(), created.id(), "127.0.0.1").success())
                .isTrue();
        assertThat(providers.synchronizeModels(owner.id(), created.id(), "127.0.0.1"))
                .singleElement()
                .satisfies(
                        model -> {
                            assertThat(model.modelId()).isEqualTo("fake-chat-v1");
                            assertThat(model.origin().name()).isEqualTo("DISCOVERED");
                        });
        ProviderConnectionView selected =
                providers.selectDefaultModel(owner.id(), created.id(), "fake-chat-v1");
        assertThat(selected.defaultModelId()).isEqualTo("fake-chat-v1");

        mockMvc.perform(post("/api/providers/{id}/test", created.id()).with(user(principal(owner))))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/providers/{id}/test", created.id())
                                .with(user(principal(owner)))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerRequestId").value("fake-request"));
    }

    @Test
    void providerRequestBodiesCannotExposeCredentialFieldsInResponses() throws Exception {
        UserAccount owner = register("admin@example.test", "Admin");

        mockMvc.perform(
                        post("/api/providers")
                                .with(user(principal(owner)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "displayName": "OpenAI via API",
                                          "providerType": "OPENAI",
                                          "apiKey": "openai-secret-from-request"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialMasked").value("••••uest"))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.credential").doesNotExist());
    }

    private SaveProviderCommand command(
            String displayName,
            ProviderType type,
            String apiKey,
            String baseUrl,
            String region,
            String configuredModelId) {
        return new SaveProviderCommand(
                displayName,
                type,
                apiKey,
                baseUrl,
                region,
                null,
                null,
                null,
                configuredModelId,
                "127.0.0.1");
    }

    private UserAccount register(String email, String displayName) {
        return identity.register(
                new RegisterCommand(
                        email, displayName, "correct-horse-battery-staple", "127.0.0.1"));
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
