package com.github.yehortpk.parser.migration;

import com.github.yehortpk.parser.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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
 * applies and that {@code spring.jpa.hibernate.ddl-auto=validate} accepts it. The context
 * loading at all is itself the validation assertion, since Hibernate checks every entity
 * against the migrated schema on startup.
 */
@DataJpaTest
class FlywayMigrationTest extends PostgresContainerTest {
    private static final String INSERT_COMPANY =
            "INSERT INTO company (company_id, title, parsing_enabled) VALUES (900, 'Probe', false)";
    private static final String INSERT_PARSED_VACANCY =
            "INSERT INTO parsed_vacancy (link, company_id, parsed_at) VALUES ('https://probe.example/1', 900, now())";

    @Autowired
    private DataSource dataSource;

    private long countWhere(String table, String condition) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT count(*) FROM " + table + " WHERE " + condition)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String findColumnType(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT data_type FROM information_schema.columns"
                             + " WHERE table_schema = 'parser' AND table_name = '" + table + "'"
                             + " AND column_name = '" + column + "'")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    /**
     * Runs the statements in one rolled-back transaction and reports whether any was refused.
     * The probe rows never survive.
     */
    private boolean isAnyStatementRejected(String... statements) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
                return false;
            } catch (SQLException ex) {
                return true;
            } finally {
                connection.rollback();
            }
        }
    }

    /**
     * Inserts a row without an id and returns the generated one, in a rolled-back transaction.
     */
    private long findGeneratedId(String insertReturningId, String... statementsBefore) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statementsBefore) {
                    statement.execute(sql);
                }
                try (ResultSet resultSet = statement.executeQuery(insertReturningId)) {
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
        assertThat(countWhere("flyway_schema_history", "success")).isPositive();
    }

    @Test
    void Migrate_FreshDatabase_ShouldKeepEverythingInTheParserSchema() throws SQLException {
        // The database is shared with the other services, each in a schema of its own
        assertThat(countWhere("information_schema.tables", "table_schema = 'parser' AND table_name IN"
                + " ('company', 'company_data', 'company_headers', 'company_cookies', 'parsed_vacancy', 'admin',"
                + " 'flyway_schema_history')")).isEqualTo(7);
        assertThat(countWhere("information_schema.tables", "table_schema = 'public'")).isZero();
    }

    @Test
    void Migrate_BooleanColumns_ShouldBeTypedBooleansNotBitStrings() throws SQLException {
        assertThat(findColumnType("company", "parsing_enabled")).isEqualTo("boolean");
    }

    @Test
    void Migrate_IdentityColumns_ShouldGenerateIdsWithoutAnExplicitValue() throws SQLException {
        // The entities use GenerationType.IDENTITY, so every generated key column has to be
        // an identity column; inserting without one would otherwise violate NOT NULL
        assertThat(findGeneratedId("INSERT INTO company_data (data_key, data_value, company_id)"
                + " VALUES ('probe', 'probe', 900) RETURNING id", INSERT_COMPANY)).isPositive();
        assertThat(findGeneratedId("INSERT INTO company_headers (header_key, header_value, company_id)"
                + " VALUES ('probe', 'probe', 900) RETURNING id", INSERT_COMPANY)).isPositive();
        assertThat(findGeneratedId("INSERT INTO company_cookies (cookie_key, cookie_value, company_id)"
                + " VALUES ('probe', 'probe', 900) RETURNING id", INSERT_COMPANY)).isPositive();
        assertThat(findGeneratedId(INSERT_PARSED_VACANCY + " RETURNING id", INSERT_COMPANY)).isPositive();
    }

    @Test
    void Migrate_ParsedVacancyTable_ShouldEnforceUniqueLink() throws SQLException {
        // One row per published link is what keeps a vacancy from being published twice
        assertThat(isAnyStatementRejected(INSERT_COMPANY, INSERT_PARSED_VACANCY)).isFalse();
        assertThat(isAnyStatementRejected(INSERT_COMPANY, INSERT_PARSED_VACANCY, INSERT_PARSED_VACANCY)).isTrue();
    }

    @Test
    void Migrate_ParsedVacancyTable_ShouldRejectAnUnknownCompany() throws SQLException {
        assertThat(isAnyStatementRejected(INSERT_PARSED_VACANCY)).isTrue();
    }

    @Test
    void Migrate_AdminTable_ShouldEnforceUniqueUsername() throws SQLException {
        String insertAdmin = "INSERT INTO admin (username, password_hash, created_at) VALUES ('probe', 'hash', now())";

        assertThat(isAnyStatementRejected(insertAdmin)).isFalse();
        assertThat(isAnyStatementRejected(insertAdmin, insertAdmin)).isTrue();
    }
}
