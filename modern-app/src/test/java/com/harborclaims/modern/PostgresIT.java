package com.harborclaims.modern;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL for the whole test run, built exactly like docker-compose builds it: the same init scripts (role, schema, seed,
 * grants) from legacy-app/db, then the modern Flyway migrations run as the OWNER with the legacy schema as the baseline.
 * The application itself connects as harbor_app (DML only), so a missing grant shows up as a failing test.
 */
public abstract class PostgresIT {

    public static final String OWNER = "harbor";
    public static final String OWNER_PASSWORD = "owner-pass-for-tests";
    public static final String APP_USER = "harbor_app";
    public static final String APP_PASSWORD = "app-pass-for-tests";
    public static final String SECRET = "test-secret-0123456789-abcdefghijkl";

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("harbor").withUsername(OWNER).withPassword(OWNER_PASSWORD).withEnv("APP_DB_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(script("00-app-role.sh", 0755), "/docker-entrypoint-initdb.d/00-app-role.sh")
            .withCopyFileToContainer(script("V1__schema.sql", 0644), "/docker-entrypoint-initdb.d/10-schema.sql")
            .withCopyFileToContainer(script("V2__seed.sql", 0644), "/docker-entrypoint-initdb.d/20-seed.sql")
            .withCopyFileToContainer(script("V3__m2_schema.sql", 0644), "/docker-entrypoint-initdb.d/30-m2-schema.sql")
            .withCopyFileToContainer(script("V5__auth_bridge.sql", 0644), "/docker-entrypoint-initdb.d/35-auth-bridge.sql")
            .withCopyFileToContainer(script("V4__grants.sql", 0644), "/docker-entrypoint-initdb.d/40-grants.sql");

    static {
        POSTGRES.start();
        flyway(POSTGRES.getJdbcUrl()).migrate();
    }

    private static MountableFile script(String name, int mode) {
        return MountableFile.forHostPath(Path.of("..", "legacy-app", "db", name).toAbsolutePath().normalize(), mode);
    }

    /** Flyway as the owner. The legacy schema (version 5) is the baseline; the modern migrations start at version 6. */
    public static Flyway flyway(String jdbcUrl) {
        return Flyway.configure().dataSource(jdbcUrl, OWNER, OWNER_PASSWORD).baselineOnMigrate(true).baselineVersion("5")
                .locations("classpath:db/migration").load();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("harbor.auth.secret", () -> SECRET);
        registry.add("harbor.auth.previous-secret", () -> "");
        registry.add("harbor.auth.allow-placeholder-secret", () -> "false");
    }
}
