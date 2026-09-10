package nl.haystaq.tijdwijs.personeel.api;

import nl.haystaq.tijdwijs.support.AbstractIntegrationIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Eerste integratietests over de volledige keten (F0.5).
 * <p>
 * Doel van deze klasse is niet volledige dekking, maar bewijzen dat de
 * kwaliteitspoort werkt: applicatie op, echte Postgres, Flyway gemigreerd,
 * echte HTTP-requests, en zowel een geaccepteerd als een afgewezen geval.
 * <p>
 * Let op: er wordt op <em>HTTP-status en effect</em> geasserteerd, niet op
 * rule-codes. De API geeft die codes nu nog niet terug; dat verandert in
 * Fase 1 (Problem Details). Deze tests worden dan uitgebreid.
 */
class EmployeeApiIT extends AbstractIntegrationIT {

    /** Geldige NL-IBAN: 18 tekens en mod-97 komt uit op 1. */
    private static final String VALID_IBAN = "NL91ABNA0417164300";

    @Test
    @DisplayName("de seed levert medewerkers op via GET /api/employees")
    void listsSeededEmployees() {
        given()
                .when().get("/employees")
                .then().statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)))
                .body("employeeCode", notNullValue());
    }

    @Test
    @DisplayName("een geldige medewerker wordt aangemaakt en is daarna opvraagbaar")
    void registersValidEmployee() {
        String id = given()
                .body(validEmployee())
                .when().post("/employees")
                .then().statusCode(201)
                .body("employeeCode", equalTo("EMP-0910"))
                .extract().path("id");

        given()
                .when().get("/employees/{id}", id)
                .then().statusCode(200)
                .body("email", equalTo("nieuw.persoon@haystaq.nl"));
    }

    @Test
    @DisplayName("een IBAN die de mod-97-controle niet haalt wordt afgewezen met 400")
    void rejectsIbanThatFailsMod97() {
        Map<String, Object> payload = validEmployee();
        // Correct formaat en correcte NL-lengte, maar het controlegetal klopt niet.
        payload.put("iban", "NL91ABNA0417164301");

        given()
                .body(payload)
                .when().post("/employees")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("een dubbel personeelsnummer levert 409 op")
    void rejectsDuplicateEmployeeCode() {
        Map<String, Object> payload = validEmployee();
        payload.put("employeeCode", "EMP-0001");

        given()
                .body(payload)
                .when().post("/employees")
                .then().statusCode(409);
    }

    /**
     * Bewust een {@code PERMANENT}-contract: voor {@code INTERN} en
     * {@code FREELANCE} gelden extra, ongedocumenteerde tariefgrenzen.
     */
    private static Map<String, Object> validEmployee() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeCode", "EMP-0910");
        payload.put("firstName", "Nieuw");
        payload.put("lastName", "Persoon");
        payload.put("email", "nieuw.persoon@haystaq.nl");
        payload.put("birthDate", "1990-05-17");
        payload.put("hireDate", "2025-01-06");
        payload.put("contractType", "PERMANENT");
        payload.put("contractHours", "40.0");
        payload.put("hourlyRate", "75.00");
        payload.put("iban", VALID_IBAN);
        payload.put("phone", "+31612345678");
        payload.put("active", true);
        return payload;
    }
}
