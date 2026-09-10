package nl.haystaq.tijdwijs.testsupport;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import nl.haystaq.tijdwijs.personeel.domain.ContractType;
import nl.haystaq.tijdwijs.personeel.domain.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bewaakt de testinfrastructuur zelf.
 *
 * <p>Als {@link Fixtures} per default een ongeldig object oplevert, slaagt of
 * faalt de hele suite om de verkeerde reden. Dat is een stille fout die je pas
 * opmerkt als de poort iets doorlaat. Daarom staat hier expliciet vast dat de
 * defaults geldig zijn en dat het "one bad field"-patroon werkt.
 */
@DisplayName("Fixtures")
class FixturesTest {

    @Test
    @DisplayName("de standaardmedewerker haalt alle invarianten")
    void defaultEmployeeIsValid() {
        assertAccepted(Fixtures::employee);

        Employee employee = Fixtures.employee();
        assertThat(employee.employeeCode().value()).isEqualTo(Fixtures.VALID_EMPLOYEE_CODE);
        assertThat(employee.iban().value()).isEqualTo(Fixtures.VALID_IBAN);
        assertThat(employee.isActive()).isTrue();
    }

    @Test
    @DisplayName("het standaarddienstverband haalt alle invarianten")
    void defaultEmploymentPeriodIsValid() {
        assertAccepted(Fixtures::employmentPeriod);
        assertThat(Fixtures.employmentPeriod().endDate()).isNull();
    }

    @Test
    @DisplayName("één ongeldig veld levert precies die rule-code op")
    void oneBadFieldYieldsThatCode() {
        // Bewijst het patroon: alle overige velden blijven geldig, dus de
        // gerapporteerde code hoort bij het veld dat is overschreven.
        assertInvalid("contract_hours.max", () -> Fixtures.employeeBuilder().contractHours("41.00").build());
        assertInvalid("phone.format", () -> Fixtures.employeeBuilder().phone("abc").build());
        assertInvalid("intern.rate", () -> Fixtures.employeeBuilder()
                .contractType(ContractType.INTERN)
                .hourlyRate("95.00")
                .build());
    }

    @Test
    @DisplayName("de tweede IBAN is een geldig alternatief")
    void alternativeIbanIsValid() {
        assertAccepted(() -> Fixtures.employeeBuilder().iban(Fixtures.VALID_IBAN_ALT).build());
    }
}
