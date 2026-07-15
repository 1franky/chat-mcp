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

@SpringBootTest
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

        assertThat(vectorExtensions).isOne();
        assertThat(schemas).containsExactly("audit", "chat", "identity", "rag");
    }
}
