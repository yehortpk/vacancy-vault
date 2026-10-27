package com.github.yehortpk.router;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need the real schema.
 *
 * <p>The migrations are hand-written SQL and {@code ddl-auto=validate} checks the entities
 * against them, so only an actual PostgreSQL instance can confirm either.
 *
 * <p>The container is started once in a static initializer and never stopped, so every
 * subclass in the run shares it. JUnit's {@code @Container} is deliberately not used: on an
 * abstract base it gives each test class its own lifecycle, and the first class to finish
 * tears the database down underneath the classes still running — which surfaces as the next
 * class's Hikari pool timing out rather than as anything resembling the real cause. Ryuk
 * removes the container when the JVM exits.
 *
 * <p>The datasource is pointed at the container by {@code @DynamicPropertySource} rather
 * than {@code @ServiceConnection}, because these properties must override the production
 * {@code application.properties}, which is still on the test classpath and whose
 * {@code notifier-postgres} url would otherwise be used.
 */
@ActiveProfiles("test")
public abstract class PostgresContainerTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.2");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
