package nl.haystaq.tijdwijs.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Levert de Postgres-container voor integratietests (F0.3).
 * <p>
 * {@link ServiceConnection} zorgt dat Spring Boot de datasource-URL, gebruiker
 * en wachtwoord automatisch overneemt; er is dus geen {@code @DynamicPropertySource}
 * nodig. Dezelfde image als productie ({@code postgres:16-alpine}) zodat het
 * schema en de Flyway-migraties zich identiek gedragen.
 * <p>
 * De container is {@code static} en wordt door Testcontainers hergebruikt over
 * alle testklassen binnen dezelfde JVM. Reuse-mode staat aan, maar werkt alleen
 * als de ontwikkelaar dat lokaal heeft ingeschakeld; in CI wordt de container
 * gewoon opnieuw gestart.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("tijdwijs")
                .withUsername("tijdwijs")
                .withPassword("tijdwijs")
                .withReuse(true);
    }
}
