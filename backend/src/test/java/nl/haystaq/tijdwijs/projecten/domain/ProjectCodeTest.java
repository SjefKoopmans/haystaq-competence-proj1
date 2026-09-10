package nl.haystaq.tijdwijs.projecten.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link ProjectCode}. Patroon: zie {@code IbanTest}. */
@DisplayName("ProjectCode")
class ProjectCodeTest {

    @Nested
    @DisplayName("code.format")
    class Format {

        @ParameterizedTest
        @NullSource
        @DisplayName("weigert een ontbrekende waarde met de formaatcode")
        void rejectsNull(String value) {
            assertInvalid("code.format", () -> new ProjectCode(value));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> code.format")
        @ValueSource(
                strings = {
                    "",
                    "PRJ-2026-1", // één cijfer in het reeksnummer
                    "PRJ-2026-0001", // vier cijfers in het reeksnummer
                    "PRJ-26-001", // tweecijferig jaar
                    "prj-2026-001", // kleine letters
                    "PRJ2026001", // geen koppeltekens
                    "PRJ-2026_001", // verkeerd scheidingsteken
                    "PROJ-2026-001", // verkeerd voorvoegsel
                    " PRJ-2026-001", // voorloopruimte
                    "PRJ-2026-001 " // sluitruimte
                })
        @DisplayName("eist exact het patroon PRJ-9999-999")
        void rejectsMalformed(String value) {
            assertInvalid("code.format", () -> new ProjectCode(value));
        }
    }

    @Nested
    @DisplayName("geldige invoer")
    class Accepted {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"PRJ-2026-001", "PRJ-0000-000", "PRJ-9999-999"})
        @DisplayName("accepteert vier jaarcijfers en drie reekscijfers")
        void acceptsValid(String value) {
            // KARAKTERISERING: het jaartal wordt niet begrensd zoals bij IsoWeek
            // (2000-2100). PRJ-0000-000 is een geldige code.
            assertAccepted(() -> new ProjectCode(value));
        }

        @Test
        @DisplayName("toString geeft de waarde")
        void toStringReturnsValue() {
            assertThat(new ProjectCode("PRJ-2026-001")).hasToString("PRJ-2026-001");
        }

        @Test
        @DisplayName("gelijke codes zijn gelijk")
        void equality() {
            assertThat(new ProjectCode("PRJ-2026-001"))
                    .isEqualTo(new ProjectCode("PRJ-2026-001"))
                    .isNotEqualTo(new ProjectCode("PRJ-2026-002"));
        }
    }

    @Nested
    @DisplayName("year")
    class Year {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({"PRJ-2026-001, 2026", "PRJ-1999-042, 1999", "PRJ-0000-000, 0", "PRJ-9999-999, 9999"})
        @DisplayName("leest het jaartal uit de code")
        void extractsYear(String value, int expected) {
            assertThat(new ProjectCode(value).year()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("JpaConverter")
    class Converter {

        private final ProjectCode.JpaConverter converter = new ProjectCode.JpaConverter();

        @Test
        @DisplayName("bewaart de waarde en leest hem identiek terug")
        void roundTrip() {
            ProjectCode original = new ProjectCode("PRJ-2026-001");
            String column = converter.convertToDatabaseColumn(original);

            assertThat(column).isEqualTo("PRJ-2026-001");
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
            assertInvalid("code.format", () -> converter.convertToEntityAttribute("PRJ-1"));
        }
    }
}
