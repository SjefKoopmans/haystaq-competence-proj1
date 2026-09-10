package nl.haystaq.tijdwijs.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import nl.haystaq.tijdwijs.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Karakteriseringstests voor {@link RestExceptionHandler}.
 *
 * <p>Fase 1 lost de tweede blocker uit de nulmeting op: de handler gooide de
 * rule-code weg, waardoor een client (of agent) niet kon vaststellen welke
 * regel precies was overtreden. Deze tests bewaken het nieuwe contract:
 *
 * <ul>
 *   <li>de body is RFC 9457 Problem Details ({@code type/title/status});
 *   <li>de rule-code ({@code code()} van {@link BusinessRuleViolation}) staat
 *       machine-leesbaar in het veld {@code code};
 *   <li>{@code error} blijft aanwezig, zodat {@code frontend/src/api.ts} —
 *       die op {@code payload?.error} leest — ongewijzigd blijft werken.
 * </ul>
 *
 * <p>Instantieert de handler direct (geen Spring-context nodig): dit is een
 * plain class met één constructorparameter, dus een volle {@code @WebMvcTest}
 * zou hier alleen traagheid toevoegen.
 */
@DisplayName("RestExceptionHandler")
class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler(false);

    @Nested
    @DisplayName("BusinessRuleViolation")
    class DomainViolations {

        @Test
        @DisplayName("INVALID_INPUT wordt 400 met de rule-code in 'code'")
        void invalidInputIncludesCode() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleDomain(BusinessRuleViolation.invalid("iban.mod97"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("iban.mod97");
            assertThat(body.get("status")).isEqualTo(400);
            assertThat(body.get("error")).isEqualTo("invalid input");
        }

        @Test
        @DisplayName("CONFLICT wordt 409 met de rule-code in 'code'")
        void conflictIncludesCode() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleDomain(BusinessRuleViolation.conflict("timesheet.duplicate"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("timesheet.duplicate");
            assertThat(body.get("error")).isEqualTo("conflict");
        }

        @Test
        @DisplayName("NOT_FOUND wordt 404 met de rule-code in 'code'")
        void notFoundIncludesCode() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleDomain(BusinessRuleViolation.notFound("employee.missing"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("employee.missing");
            assertThat(body.get("error")).isEqualTo("not found");
        }
    }

    @Nested
    @DisplayName("overige fouten")
    class OtherFailures {

        @Test
        @DisplayName("een generieke bad request krijgt geen 'code' (geen rule-code beschikbaar)")
        void badRequestHasNoCode() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleBadRequest(new IllegalArgumentException("bad json"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).doesNotContainKey("code");
            assertThat(response.getBody().get("error")).isEqualTo("invalid input");
        }

        @Test
        @DisplayName("een onverwachte fout krijgt 500 met een leesbare referentie, geen rule-code")
        void unexpectedHasReferenceNotCode() {
            ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(new RuntimeException("boom"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body).doesNotContainKey("code");
            assertThat(body.get("ref")).isNotNull();
            assertThat(body.get("error")).isEqualTo("internal error");
        }
    }
}
