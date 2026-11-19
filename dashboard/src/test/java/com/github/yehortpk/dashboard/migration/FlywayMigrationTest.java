package com.github.yehortpk.dashboard.migration;

import com.github.yehortpk.dashboard.PostgresContainerTest;
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
    private static final String INSERT_VACANCY =
            "INSERT INTO vacancy (is_remote, link, title, company_id)"
                    + " VALUES (false, 'https://probe.example/1', 'Probe', 900)";

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

    @Test
    void Migrate_FreshDatabase_ShouldApplyEveryMigration() throws SQLException {
        // A failed migration leaves a row with success = false
        assertThat(countWhere("flyway_schema_history", "success = false")).isZero();
        assertThat(countWhere("flyway_schema_history", "success")).isPositive();
    }

    @Test
    void Migrate_FreshDatabase_ShouldKeepEverythingInTheDashboardSchema() throws SQLException {
        // The database is shared with the other services, each in a schema of its own
        assertThat(countWhere("information_schema.tables",
                "table_schema = 'dashboard' AND table_name IN ('vacancy', 'flyway_schema_history')")).isEqualTo(2);
        assertThat(countWhere("information_schema.tables", "table_schema = 'public'")).isZero();
    }

    @Test
    void Migrate_VacancyWithoutSalary_ShouldBeAccepted() throws SQLException {
        assertThat(isAnyStatementRejected(INSERT_VACANCY)).isFalse();
    }

    @Test
    void Migrate_VacancyTable_ShouldEnforceUniqueLink() throws SQLException {
        // The link is what a redelivered vacancy is recognized by
        assertThat(isAnyStatementRejected(INSERT_VACANCY, INSERT_VACANCY)).isTrue();
    }

    @Test
    void Migrate_VacancyTable_ShouldRequireACompany() throws SQLException {
        assertThat(isAnyStatementRejected(
                "INSERT INTO vacancy (is_remote, link, title) VALUES (false, 'https://probe.example/2', 'Probe')"))
                .isTrue();
    }
}
