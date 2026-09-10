package nl.haystaq.tijdwijs.personeel.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link EmployeeCode}. Patroon: zie {@code IbanTest}. */
@DisplayName("EmployeeCode")
class EmployeeCodeTest {

    @Nested
    @DisplayName("employee_code.format")
    class Format {

        @ParameterizedTest
        @NullSource
        @DisplayName("weigert een ontbrekende waarde met de formaatcode")
        void rejectsNull(String value) {
            // KARAKTERISERING: er is geen aparte `.missing`-code. Null en een
            // verkeerd formaat leveren beide employee_code.format op.
            assertInvalid("employee_code.format", () -> new EmployeeCode(value));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> employee_code.format")
        @ValueSource(
                strings = {
                    "",
                    "EMP-001", // drie cijfers
                    "EMP-00001", // vijf cijfers
                    "emp-0001", // kleine letters
                    "EMP0001", // geen koppelteken
                    "EMP-ABCD", // letters in plaats van cijfers
                    " EMP-0001", // voorloopruimte, wordt niet getrimd
                    "EMP-0001 ", // sluitruimte
                    "XEMP-0001",
                    "EMP-0001X"
                })
        @DisplayName("eist exact het patroon EMP-9999")
        void rejectsMalformed(String value) {
            assertInvalid("employee_code.format", () -> new EmployeeCode(value));
        }
    }

    @Nested
    @DisplayName("geldige invoer")
    class Accepted {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"EMP-0000", "EMP-0001", "EMP-9999"})
        @DisplayName("accepteert vier cijfers, inclusief de grenzen")
        void acceptsValid(String value) {
            assertAccepted(() -> new EmployeeCode(value));
        }

        @Test
        @DisplayName("bewaart de waarde ongewijzigd")
        void keepsValueAsIs() {
            assertThat(new EmployeeCode("EMP-0042").value()).isEqualTo("EMP-0042");
        }

        @Test
        @DisplayName("toString geeft de waarde")
        void toStringReturnsValue() {
            assertThat(new EmployeeCode("EMP-0042")).hasToString("EMP-0042");
        }

        @Test
        @DisplayName("gelijke codes zijn gelijk")
        void equality() {
            assertThat(new EmployeeCode("EMP-0042"))
                    .isEqualTo(new EmployeeCode("EMP-0042"))
                    .isNotEqualTo(new EmployeeCode("EMP-0043"));
        }
    }

    @Nested
    @DisplayName("JpaConverter")
    class Converter {

        private final EmployeeCode.JpaConverter converter = new EmployeeCode.JpaConverter();

        @Test
        @DisplayName("bewaart de waarde en leest hem identiek terug")
        void roundTrip() {
            EmployeeCode original = new EmployeeCode("EMP-0042");
            String column = converter.convertToDatabaseColumn(original);

            assertThat(column).isEqualTo("EMP-0042");
            assertThat(converter.convertToEntityAttribute(column)).isEqualTo(original);
        }

        @Test
        @DisplayName("null blijft null in beide richtingen")
        void nullSafe() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("een ongeldige waarde uit de database wordt geweigerd")
        void validatesOnRead() {
            assertInvalid("employee_code.format", () -> converter.convertToEntityAttribute("EMP-1"));
        }
    }
}
