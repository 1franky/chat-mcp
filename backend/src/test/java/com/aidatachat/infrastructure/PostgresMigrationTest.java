package com.aidatachat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        properties =
                "app.providers.credential-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class PostgresMigrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesVectorExtensionAndBoundedSchemas() {
        Integer vectorExtensions =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_extension WHERE extname = 'vector'",
                        Integer.class);
        List<String> schemas =
                jdbcTemplate.queryForList(
                        "SELECT schema_name FROM information_schema.schemata "
                                + "WHERE schema_name IN ('identity', 'chat', 'rag', 'audit') "
                                + "ORDER BY schema_name",
                        String.class);
        List<String> sprintOneTables =
                jdbcTemplate.queryForList(
                        "SELECT table_schema || '.' || table_name "
                                + "FROM information_schema.tables "
                                + "WHERE (table_schema = 'identity' AND table_name IN "
                                + "('app_user', 'spring_session', 'spring_session_attributes')) "
                                + "OR (table_schema = 'audit' "
                                + "AND table_name = 'security_audit_event') "
                                + "ORDER BY table_schema, table_name",
                        String.class);
        List<String> sprintTwoTables =
                jdbcTemplate.queryForList(
                        "SELECT table_schema || '.' || table_name "
                                + "FROM information_schema.tables "
                                + "WHERE table_schema = 'chat' "
                                + "AND table_name IN ('provider_connection', 'provider_model') "
                                + "ORDER BY table_name",
                        String.class);
        List<String> sprintThreeTables =
                jdbcTemplate.queryForList(
                        "SELECT table_schema || '.' || table_name "
                                + "FROM information_schema.tables "
                                + "WHERE table_schema = 'chat' "
                                + "AND table_name IN ('conversation', 'message') "
                                + "ORDER BY table_name",
                        String.class);
        List<String> sprintFourTables =
                jdbcTemplate.queryForList(
                        "SELECT table_schema || '.' || table_name "
                                + "FROM information_schema.tables "
                                + "WHERE table_schema = 'chat' "
                                + "AND table_name = 'message_tool_call' "
                                + "ORDER BY table_name",
                        String.class);

        assertThat(vectorExtensions).isOne();
        assertThat(schemas).containsExactly("audit", "chat", "identity", "rag");
        assertThat(sprintOneTables)
                .containsExactly(
                        "audit.security_audit_event",
                        "identity.app_user",
                        "identity.spring_session",
                        "identity.spring_session_attributes");
        assertThat(sprintTwoTables)
                .containsExactly("chat.provider_connection", "chat.provider_model");
        assertThat(sprintThreeTables).containsExactly("chat.conversation", "chat.message");
        assertThat(sprintFourTables).containsExactly("chat.message_tool_call");
    }
}
