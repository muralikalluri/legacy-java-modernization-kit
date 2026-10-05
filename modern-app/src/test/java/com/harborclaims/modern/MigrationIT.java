package com.harborclaims.modern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

/** The database side: the baseline, the modern migration, and the rights of the application user. */
class MigrationIT extends PostgresIT {

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }

    private static Connection app() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
    }

    @Test
    void theLegacySchemaIsTheBaselineAndTheModernMigrationRanOnTop() throws SQLException {
        try (Connection c = owner(); Statement st = c.createStatement();
                var rs = st.executeQuery("SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("5");
            assertThat(rs.getString("type")).isEqualTo("BASELINE");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("6");
            assertThat(rs.getBoolean("success")).isTrue();
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void theDatabaseNowEnforcesCaseInsensitiveUniqueEmails() throws SQLException {
        try (Connection c = owner(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO customers (first_name, last_name, email) VALUES ('A', 'Dup', 'mixed.case@example.test')");
            assertThatThrownBy(() -> st.executeUpdate("INSERT INTO customers (first_name, last_name, email) VALUES ('B', 'Dup', 'MIXED.CASE@example.test')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uq_customers_email_ci");
            st.executeUpdate("DELETE FROM customers WHERE last_name = 'Dup'");
        }
    }

    @Test
    void theApplicationUserCannotChangeTheSchema() throws SQLException {
        try (Connection c = app(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate("CREATE TABLE should_not_exist (id int)")).isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("DROP TABLE customers")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.executeUpdate("ALTER TABLE customers ADD COLUMN x int")).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void theApplicationUserCanStillDoWhatTheAppNeeds() throws SQLException {
        try (Connection c = app(); Statement st = c.createStatement()) {
            assertThat(st.executeQuery("SELECT count(*) FROM customers").next()).isTrue();
            assertThat(st.executeUpdate("INSERT INTO auth_revocations (sid, revoked_at_ms) VALUES ('migration-it', 1)")).isEqualTo(1);
            assertThat(st.executeUpdate("DELETE FROM auth_revocations WHERE sid = 'migration-it'")).isEqualTo(1);
            assertThat(st.executeUpdate("UPDATE app_users SET active = active WHERE username = 'admin'")).isEqualTo(1);
        }
    }

    @Test
    void anEmptyDatabaseMakesTheMigrationFailInsteadOfPretendingToSucceed() throws SQLException {
        try (Connection c = owner(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE empty_db");
        }
        String url = POSTGRES.getJdbcUrl().replace("/harbor", "/empty_db");
        assertThatThrownBy(() -> flyway(url).migrate()).isInstanceOf(FlywayException.class);
    }
}
