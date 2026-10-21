package com.github.yehortpk.router.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Flyway migrations against a real PostgreSQL instance.
 *
 * <p>The schema is hand-written SQL, so nothing but an actual database can confirm it
 * applies and that {@code spring.jpa.hibernate.ddl-auto=validate} accepts it — a
 * mismatch between a migration's column type and its entity fails this test rather
 * than the next container start. The context loading at all is itself the validation
 * assertion, since Hibernate checks every entity against the migrated schema on startup.
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
class FlywayMigrationTest {

    @TestConfiguration
    static class PostgresContainerConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:16.2");
        }
    }

    @Autowired
    private DataSource dataSource;

    private long countRows(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long countWhere(String table, String condition) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT count(*) FROM " + table + " WHERE " + condition)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String columnType(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT data_type FROM information_schema.columns"
                             + " WHERE table_name = '" + table + "'"
                             + " AND column_name = '" + column + "'")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    /**
     * Inserts a row without an id and returns the generated one.
     *
     * <p>Rolled back explicitly: these helpers open their own JDBC connection, which the
     * surrounding {@code @DataJpaTest} transaction does not cover, so a committed probe row
     * would leak into the other tests' row counts.
     */
    private long insertedCompanyDataId() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO company (company_id, title, parsing_enabled)"
                        + " VALUES (1, 'IdentityProbe', false)");
                try (ResultSet resultSet = statement.executeQuery(
                        "INSERT INTO company_data (data_key, data_value, company_id)"
                                + " VALUES ('probe', 'probe', 1) RETURNING id")) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void Migrate_FreshDatabase_ShouldApplyEveryMigration() throws SQLException {
        // A failed migration leaves a row with success = false
        assertThat(countWhere("flyway_schema_history", "success = false")).isZero();
        assertThat(countRows("flyway_schema_history")).isPositive();
    }

    @Test
    void Migrate_FreshDatabase_ShouldCreateEverySchemaTable() throws SQLException {
        // Each table is queried rather than looked up in the catalog, so a missing one
        // or a misspelled column fails here
        assertThat(countRows("company")).isZero();
        assertThat(countRows("company_data")).isZero();
        assertThat(countRows("company_headers")).isZero();
        assertThat(countRows("company_cookies")).isZero();
        assertThat(countRows("vacancy")).isZero();
        assertThat(countRows("client")).isZero();
        assertThat(countRows("filter")).isZero();
    }

    @Test
    void Migrate_BooleanColumns_ShouldBeTypedBooleansNotBitStrings() throws SQLException {
        // MySQL stored these as bit(1); on Postgres they must be real booleans, or the
        // entities' `boolean` fields fail to bind
        assertThat(columnType("company", "parsing_enabled")).isEqualTo("boolean");
        assertThat(columnType("vacancy", "is_remote")).isEqualTo("boolean");
    }

    @Test
    void Migrate_IdentityColumns_ShouldGenerateIdsWithoutAnExplicitValue() throws SQLException {
        // The entities use GenerationType.IDENTITY, so every generated key column has to be
        // an identity column; inserting without one would otherwise violate NOT NULL
        assertThat(insertedCompanyDataId()).isPositive();
    }
}
