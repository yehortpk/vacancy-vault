package com.github.yehortpk.router.migration;

import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.providers.stub.StubConnectionProvider;
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
    /** The test-only stub provider's id, inserted by R__create_stub_provider.sql. */
    private static final long STUB_ID = StubConnectionProvider.PROVIDER_ID;
    /** A second provider, inserted inside the rolled-back probes only. */
    private static final long PROBE_ID = 900;
    private static final String INSERT_PROBE_PROVIDER =
            "INSERT INTO provider (id, title, link_instructions) VALUES (" + PROBE_ID + ", 'Probe', 'Use {token}')";

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
     * Runs the statements, then the count query, in one rolled-back transaction, for
     * observing what a cascade did without leaving anything behind.
     */
    private long countAfter(String countQuery, String... statements) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
                try (ResultSet resultSet = statement.executeQuery(countQuery)) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static String insertUser(String email) {
        return "INSERT INTO app_user (email, password_hash, created_at, enabled)"
                + " VALUES ('" + email + "', 'hash', now(), true)";
    }

    private static String insertConnection(long id, String email, long providerId) {
        return "INSERT INTO connection (id, user_id, provider_id, linked_at)"
                + " SELECT " + id + ", id, " + providerId + ", now() FROM app_user"
                + " WHERE email = '" + email + "'";
    }

    private static String insertStubConnection(long connectionId, String address) {
        return "INSERT INTO stub_connection (connection_id, address)"
                + " VALUES (" + connectionId + ", '" + address + "')";
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
        assertThat(countRows("connection")).isZero();
        assertThat(countRows("link_session")).isZero();
        assertThat(countRows("stub_connection")).isZero();
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
    void Migrate_ProviderCatalog_ShouldHoldNoRowsOfItsOwn() throws SQLException {
        // V3 creates an empty catalog; each provider's own migration inserts its row. The only
        // row here is the test-only stub's
        assertThat(countWhere("provider", "id <> " + STUB_ID)).isZero();
    }

    @Test
    void Migrate_ProviderTable_ShouldRejectInstructionsWithoutTheTokenPlaceholder() throws SQLException {
        // The core fills {token} in; instructions without it would never show the user a token
        assertThat(isAnyStatementRejected(
                "INSERT INTO provider (id, title, link_instructions) VALUES (901, 'NoToken', 'Just link it')"))
                .isTrue();
    }

    @Test
    void Migrate_DetailsTable_ShouldEnforceOneAccountPerDestination() throws SQLException {
        // A provider's UNIQUE on its destination is the whole guarantee that a destination has
        // a single owner; otherwise one chat's notifications would reach two accounts
        assertThat(isAnyStatementRejected(
                insertUser("dest-a@example.com"),
                insertUser("dest-b@example.com"),
                insertConnection(900001, "dest-a@example.com", STUB_ID),
                insertConnection(900002, "dest-b@example.com", STUB_ID),
                insertStubConnection(900001, "shared-address"),
                insertStubConnection(900002, "shared-address"))).isTrue();
    }

    @Test
    void Migrate_DetailsTable_ShouldAcceptADetailsRowOfItsOwnProvidersConnection() throws SQLException {
        // The positive case for the two tests below, so they fail for the reason they name
        assertThat(isAnyStatementRejected(
                insertUser("details@example.com"),
                insertConnection(900003, "details@example.com", STUB_ID),
                insertStubConnection(900003, "own-address"))).isFalse();
    }

    @Test
    void Migrate_DetailsTable_ShouldRejectADetailsRowOfAnotherProvidersConnection() throws SQLException {
        // The composite foreign key on (connection_id, provider_id), targeting connection's
        // UNIQUE (id, provider_id): a details row can only hang off its own provider's connection
        assertThat(isAnyStatementRejected(
                INSERT_PROBE_PROVIDER,
                insertUser("mismatch@example.com"),
                insertConnection(900004, "mismatch@example.com", PROBE_ID),
                insertStubConnection(900004, "mismatched-address"))).isTrue();
    }

    @Test
    void Migrate_DetailsTable_ShouldRejectAnotherProviderId() throws SQLException {
        assertThat(isAnyStatementRejected(
                INSERT_PROBE_PROVIDER,
                insertUser("check@example.com"),
                insertConnection(900005, "check@example.com", PROBE_ID),
                "INSERT INTO stub_connection (connection_id, provider_id, address)"
                        + " VALUES (900005, " + PROBE_ID + ", 'checked-address')")).isTrue();
    }

    @Test
    void Migrate_ConnectionDeleted_ShouldCascadeToItsDetails() throws SQLException {
        // Unlinking only ever deletes the connection row and relies on this cascade
        assertThat(countAfter("SELECT count(*) FROM stub_connection WHERE connection_id = 900006",
                insertUser("cascade@example.com"),
                insertConnection(900006, "cascade@example.com", STUB_ID),
                insertStubConnection(900006, "cascaded-address"),
                "DELETE FROM connection WHERE id = 900006")).isZero();
    }

    @Test
    void Migrate_LinkSessionTable_ShouldEnforceUniqueTokenHash() throws SQLException {
        // The hash is how a reported token finds its session; two sessions sharing one would
        // let one token link to an arbitrary owner
        assertThat(isAnyStatementRejected(
                insertUser("token@example.com"),
                "INSERT INTO link_session (user_id, provider_id, token_hash, created_at, expires_at)"
                        + " SELECT id, " + STUB_ID + ", 'samehash', now(), now() FROM app_user"
                        + " WHERE email = 'token@example.com'",
                "INSERT INTO link_session (user_id, provider_id, token_hash, created_at, expires_at)"
                        + " SELECT id, " + STUB_ID + ", 'samehash', now(), now() FROM app_user"
                        + " WHERE email = 'token@example.com'")).isTrue();
    }

    @Test
    void Migrate_IdentityColumns_ShouldGenerateIdsWithoutAnExplicitValue() throws SQLException {
        // The entities use GenerationType.IDENTITY, so every generated key column has to be
        // an identity column; inserting without one would otherwise violate NOT NULL
        assertThat(insertedCompanyDataId()).isPositive();
    }
}
