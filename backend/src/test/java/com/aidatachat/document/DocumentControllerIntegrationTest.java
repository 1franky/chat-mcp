package com.aidatachat.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.port.in.DocumentManagementUseCase;
import com.aidatachat.application.port.in.DocumentManagementUseCase.UploadDocumentCommand;
import com.aidatachat.application.port.in.DocumentManagementUseCase.UploadDocumentResult;
import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.domain.model.UserAccount;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        properties = {
            "app.providers.credential-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "app.integrations.mode=real"
        })
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class DocumentControllerIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_document_web_test")
                    .withUsername("test")
                    .withPassword("test");

    @TempDir static Path storagePath;

    @DynamicPropertySource
    static void ragStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rag.storage.path", () -> storagePath.toString());
    }

    @Autowired private IdentityUseCase identity;
    @Autowired private DocumentManagementUseCase documentsUseCase;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM rag.message_document");
        jdbc.update("DELETE FROM rag.document_chunk");
        jdbc.update("DELETE FROM rag.document");
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
    void uploadsAndListsADocumentOverHttp() throws Exception {
        UserAccount owner = register("owner@example.test");

        mockMvc.perform(
                        multipart("/api/documents")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "informe.pdf",
                                                "application/pdf",
                                                DocumentFixtures.pdfBytes()))
                                .with(user(principal(owner)))
                                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        mockMvc.perform(get("/api/documents").with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].originalFilename").value("informe.pdf"));
    }

    @Test
    void rejectsUploadWithoutCsrf() throws Exception {
        UserAccount owner = register("owner@example.test");

        mockMvc.perform(
                        multipart("/api/documents")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "informe.pdf",
                                                "application/pdf",
                                                DocumentFixtures.pdfBytes()))
                                .with(user(principal(owner))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUploadWithoutAuthentication() throws Exception {
        mockMvc.perform(
                        multipart("/api/documents")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "informe.pdf",
                                                "application/pdf",
                                                DocumentFixtures.pdfBytes()))
                                .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void isolatesDocumentsByOwnerViaHttp() throws Exception {
        UserAccount owner = register("owner@example.test");
        UserAccount other = register("other@example.test");
        UploadDocumentResult uploaded =
                documentsUseCase.uploadDocument(
                        owner.id(),
                        new UploadDocumentCommand(
                                "informe.pdf",
                                DocumentFixtures.pdfBytes().length,
                                new ByteArrayInputStream(DocumentFixtures.pdfBytes()),
                                "127.0.0.1"));
        String documentId = uploaded.document().id().toString();

        mockMvc.perform(get("/api/documents/{id}", documentId).with(user(principal(other))))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        delete("/api/documents/{id}", documentId)
                                .with(user(principal(other)))
                                .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/documents").with(user(principal(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(
                        delete("/api/documents/{id}", documentId)
                                .with(user(principal(owner)))
                                .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag.document", Integer.class))
                .isZero();
    }

    @Test
    void processesAnUploadedDocumentIntoReadyWithRealChunksAndEmbeddings() throws Exception {
        UserAccount owner = register("owner@example.test");
        byte[] content =
                "Contenido real y suficiente para generar al menos un chunk indexado."
                        .getBytes(StandardCharsets.UTF_8);

        UploadDocumentResult uploaded =
                documentsUseCase.uploadDocument(
                        owner.id(),
                        new UploadDocumentCommand(
                                "informe.txt",
                                content.length,
                                new ByteArrayInputStream(content),
                                "127.0.0.1"));
        UUID documentId = uploaded.document().id();

        String status = awaitTerminalStatus(documentId);

        assertThat(status).isEqualTo("READY");
        Integer chunkRowCount =
                jdbc.queryForObject(
                        "SELECT count(*) FROM rag.document_chunk WHERE document_id = ?",
                        Integer.class,
                        documentId);
        assertThat(chunkRowCount).isGreaterThan(0);
        Integer documentChunkCount =
                jdbc.queryForObject(
                        "SELECT chunk_count FROM rag.document WHERE id = ?",
                        Integer.class,
                        documentId);
        assertThat(documentChunkCount).isEqualTo(chunkRowCount);
    }

    private String awaitTerminalStatus(UUID documentId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String status =
                    jdbc.queryForObject(
                            "SELECT status FROM rag.document WHERE id = ?",
                            String.class,
                            documentId);
            if (!status.equals("UPLOADED") && !status.equals("PROCESSING")) {
                return status;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document processing did not reach a terminal state in time");
    }

    private UserAccount register(String email) {
        return identity.register(
                new RegisterCommand(
                        email, "Test User", "correct-horse-battery-staple", "127.0.0.1"));
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
