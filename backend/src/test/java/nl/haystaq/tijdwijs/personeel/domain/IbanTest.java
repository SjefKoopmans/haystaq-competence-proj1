package nl.haystaq.tijdwijs.personeel.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import nl.haystaq.tijdwijs.testsupport.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor {@link Iban}.
 *
 * <p><strong>Dit is de referentietest van de suite.</strong> Nieuwe testklassen
 * volgen dit patroon:
 *
 * <ol>
 *   <li>Eén {@code @Nested} klasse per rule-code, plus één voor geldige invoer
 *       en één voor overig gedrag.
 *   <li>Asserteren via {@code Violations.assertInvalid} / {@code assertConflict}
 *       — nooit op de foutmelding, altijd op {@code code()} én {@code kind()}.
 *   <li>{@code @ParameterizedTest} met {@code @CsvSource} of {@code @ValueSource}
 *       in plaats van tientallen bijna-identieke methodes.
 *   <li>Ook het positieve geval vastleggen. Zonder {@code assertAccepted} zou
 *       een test kunnen slagen doordat álles wordt afgewezen.
 * </ol>
 *
 * <p>Deze tests leggen vast wat de code <em>nu</em> doet, niet wat hij zou
 * moeten doen. Afwijkingen van {@code docs/business-rules.md} worden gemeld,
 * niet stilzwijgend gerepareerd.
 */
@DisplayName("Iban")
class IbanTest {

    @Nested
    @DisplayName("iban.missing")
    class Missing {

        @ParameterizedTest
        @NullSource
        @DisplayName("weigert een ontbrekende waarde")
        void rejectsNull(String value) {
            assertInvalid("iban.missing", () -> new Iban(value));
        }
    }

    @Nested
    @DisplayName("iban.format")
    class Format {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> iban.format")
        @ValueSource(
                strings = {
                    "", // leeg
                    "NL91ABNA04", // te kort: minder dan 10 tekens na het controlegetal
                    "N191ABNA0417164300", // cijfer op landcodepositie
                    "NLX1ABNA0417164300", // letter op controlecijferpositie
                    "NL91-ABNA-0417-1643-00", // koppeltekens worden niet gestript
                    "NL91ABNA0417164300ABCDEFGHIJKLMNOPQRSTUVWXYZ" // langer dan 34
                })
        @DisplayName("weigert waarden die niet aan ISO 13616 voldoen")
        void rejectsMalformed(String value) {
            assertInvalid("iban.format", () -> new Iban(value));
        }
    }

    @Nested
    @DisplayName("iban.nl_length")
    class DutchLength {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> iban.nl_length")
        @ValueSource(
                strings = {
                    "NL91ABNA04171643001", // 19 tekens
                    "NL91ABNA041716430", // 17 tekens
                    "NL91ABNA04171643" // 16 tekens
                })
        @DisplayName("eist voor NL exact 18 tekens")
        void rejectsWrongDutchLength(String value) {
            // KARAKTERISERING: de lengtecontrole komt vóór de mod-97-controle. Deze
            // waarden zijn ook mod-97-ongeldig, maar de lengtecode wint.
            assertInvalid("iban.nl_length", () -> new Iban(value));
        }

        @Test
        @DisplayName("laat een niet-NL IBAN met andere lengte toe")
        void allowsOtherCountryLengths() {
            // KARAKTERISERING: de lengtecontrole geldt alleen voor NL. Voor andere
            // landen wordt de landspecifieke lengte niet gecontroleerd, alleen mod-97.
            assertAccepted(() -> new Iban("DE89370400440532013000")); // 22 tekens
        }
    }

    @Nested
    @DisplayName("iban.mod97")
    class Mod97 {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> iban.mod97")
        @ValueSource(
                strings = {
                    "NL91ABNA0417164301", // laatste cijfer verhoogd
                    "NL92ABNA0417164300", // controlecijfer verhoogd
                    "NL00ABNA0417164300"
                })
        @DisplayName("weigert een onjuist controlegetal")
        void rejectsBadChecksum(String value) {
            assertInvalid("iban.mod97", () -> new Iban(value));
        }
    }

    @Nested
    @DisplayName("geldige invoer")
    class Accepted {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {Fixtures.VALID_IBAN, Fixtures.VALID_IBAN_ALT, "DE89370400440532013000"})
        @DisplayName("accepteert een correcte IBAN")
        void acceptsValid(String value) {
            assertAccepted(() -> new Iban(value));
        }
    }

    @Nested
    @DisplayName("normalisatie")
    class Normalisation {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
            "'NL91 ABNA 0417 1643 00', NL91ABNA0417164300",
            "'nl91abna0417164300',     NL91ABNA0417164300",
            "'  NL91ABNA0417164300  ', NL91ABNA0417164300",
            "'nl91 ABNA 0417\t164300', NL91ABNA0417164300"
        })
        @DisplayName("strippt witruimte en maakt hoofdletters")
        void normalises(String input, String expected) {
            assertThat(new Iban(input).value()).isEqualTo(expected);
        }

        @Test
        @DisplayName("toString geeft de genormaliseerde waarde")
        void toStringReturnsValue() {
            assertThat(new Iban("nl91 abna 0417 1643 00")).hasToString(Fixtures.VALID_IBAN);
        }

        @Test
        @DisplayName("twee schrijfwijzen van dezelfde IBAN zijn gelijk")
        void equalityIgnoresFormatting() {
            assertThat(new Iban("NL91 ABNA 0417 1643 00")).isEqualTo(new Iban("nl91abna0417164300"));
        }
    }

    @Nested
    @DisplayName("JpaConverter")
    class Converter {

        private final Iban.JpaConverter converter = new Iban.JpaConverter();

        @Test
        @DisplayName("bewaart de waarde en leest hem identiek terug")
        void roundTrip() {
            Iban original = new Iban(Fixtures.VALID_IBAN);

            String column = converter.convertToDatabaseColumn(original);

            assertThat(column).isEqualTo(Fixtures.VALID_IBAN);
            assertThat(converter.convertToEntityAttribute(column)).isEqualTo(original);
        }

        @Test
        @DisplayName("null blijft null in beide richtingen")
        void nullSafe() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("een ongeldige waarde uit de database wordt alsnog geweigerd")
        void validatesOnRead() {
            // KARAKTERISERING: de converter valideert bij het lezen. Corrupte data
            // in de kolom leidt dus tot een BusinessRuleViolation, niet tot een
            // stilzwijgend ongeldig object.
            assertInvalid("iban.mod97", () -> converter.convertToEntityAttribute("NL91ABNA0417164301"));
        }
    }
}
