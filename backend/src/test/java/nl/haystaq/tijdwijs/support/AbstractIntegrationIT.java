package nl.haystaq.tijdwijs.support;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basis voor alle API-integratietests (F0.5).
 * <p>
 * De applicatie start volledig op een willekeurige poort en praat tegen een
 * echte Postgres in een Testcontainer, zodat Flyway daadwerkelijk migreert
 * ({@code ddl-auto: validate} zou anders falen). RestAssured stuurt echte
 * HTTP-requests, dus de volledige keten controller -> service -> domein ->
 * database wordt geraakt.
 * <p>
 * De container is gedeeld tussen alle testklassen (zie
 * {@link PostgresContainerConfig}); per test wordt de database via
 * {@code POST /api/admin/reset} teruggezet naar de seed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainerConfig.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void resetEnvironmentAndConfigureRestAssured() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setBasePath("/api")
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        // Zelfde effect als POST /api/admin/reset: terug naar de minimale seed,
        // zodat tests onafhankelijk van elkaar en van hun volgorde zijn.
        flyway.clean();
        flyway.migrate();
    }
}
