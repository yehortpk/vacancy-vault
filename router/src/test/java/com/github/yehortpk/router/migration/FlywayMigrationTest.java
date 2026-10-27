package com.github.yehortpk.router.migration;

import com.github.yehortpk.router.PostgresContainerTest;
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
 * applies and that {@code spring.jpa.hibernate.ddl-auto=validate} accepts it — a
 * mismatch between a migration's column type and its entity fails this test rather
 * than the next container start. The context loading at all is itself the validation
 * assertion, since Hibernate checks every entity against the migrated schema on startup.
 */
@DataJpaTest
class FlywayMigrationTest extends PostgresContainerTest {

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
     * Runs the same insert twice in a rolled-back transaction and reports whether the
     * second was refused, which is how a UNIQUE constraint is observed from outside JPA.
     */
    private boolean isSecondInsertRejected(String insert) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute(insert);
                statement.execute(insert);
                return false;
            } catch (SQLException ex) {
                return true;
            } finally {
                connection.rollback();
            }
        }
    }

    /**
     * Links the same chat id to two freshly inserted accounts and reports whether the second
     * attempt was refused. Rolled back, so neither probe row survives.
     */
    private boolean isChatIdRejectedTwice() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO app_user (email, password_hash, created_at, enabled)"
                        + " VALUES ('chat-a@example.com', 'hash', now(), true),"
                        + " ('chat-b@example.com', 'hash', now(), true)");
                statement.execute("INSERT INTO telegram_connection (user_id, chat_id, linked_at)"
                        + " SELECT id, 777000111, now() FROM app_user"
                        + " WHERE email = 'chat-a@example.com'");
                statement.execute("INSERT INTO telegram_connection (user_id, chat_id, linked_at)"
                        + " SELECT id, 777000111, now() FROM app_user"
                        + " WHERE email = 'chat-b@example.com'");
                return false;
            } catch (SQLException ex) {
                return true;
            } finally {
                connection.rollback();
            }
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
        assertThat(countRows("app_user")).isZero();
        assertThat(countRows("telegram_connection")).isZero();
    }

    @Test
    void Migrate_BooleanColumns_ShouldBeTypedBooleansNotBitStrings() throws SQLException {
        // MySQL stored these as bit(1); on Postgres they must be real booleans, or the
        // entities' `boolean` fields fail to bind
        assertThat(columnType("company", "parsing_enabled")).isEqualTo("boolean");
        assertThat(columnType("vacancy", "is_remote")).isEqualTo("boolean");
    }

    @Test
    void Migrate_AppUserTable_ShouldEnforceUniqueEmail() throws SQLException {
        // Two accounts on one address would make login ambiguous, and the duplicate is what
        // the registration race relies on the database to reject
        assertThat(isSecondInsertRejected(
                "INSERT INTO app_user (email, password_hash, created_at, enabled)"
                        + " VALUES ('dup@example.com', 'hash', now(), true)")).isTrue();
    }

    @Test
    void Migrate_AppUserTable_ShouldEnforceUniqueApiKeyPrefix() throws SQLException {
        // The prefix is the lookup key for a pasted API key; two accounts sharing one would
        // make that lookup pick an arbitrary owner
        assertThat(isSecondInsertRejected(
                "INSERT INTO app_user (email, password_hash, created_at, enabled, api_key_prefix)"
                        + " VALUES ('prefix' || floor(random() * 1e9) || '@example.com',"
                        + " 'hash', now(), true, 'samepref')")).isTrue();
    }

    @Test
    void Migrate_ProviderCatalog_ShouldContainTheTelegramRow() throws SQLException {
        // V3 seeds the catalog; ChannelProvider.TELEGRAM has to have a row to join against
        assertThat(countWhere("provider", "code = 'TELEGRAM' AND enabled")).isEqualTo(1);
    }

    @Test
    void Migrate_TelegramConnectionTable_ShouldEnforceOneAccountPerChat() throws SQLException {
        // The UNIQUE on chat_id is the whole guarantee that a chat has a single owner; the
        // bot would otherwise deliver one chat's notifications to two accounts
        assertThat(isChatIdRejectedTwice()).isTrue();
    }

    @Test
    void Migrate_TelegramConnectionChatId_ShouldBeATypedInteger() throws SQLException {
        // A stringified id in a column shared across platforms is exactly what the
        // per-provider tables exist to avoid
        assertThat(columnType("telegram_connection", "chat_id")).isEqualTo("bigint");
    }

    @Test
    void Migrate_IdentityColumns_ShouldGenerateIdsWithoutAnExplicitValue() throws SQLException {
        // The entities use GenerationType.IDENTITY, so every generated key column has to be
        // an identity column; inserting without one would otherwise violate NOT NULL
        assertThat(insertedCompanyDataId()).isPositive();
    }
}
