package nl.haystaq.tijdwijs.shared.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link Hours}. Patroon: zie {@code IbanTest}. */
@DisplayName("Hours")
class HoursTest {

    @Nested
    @DisplayName("hours.missing")
    class Missing {

        @Test
        @DisplayName("weigert null")
        void rejectsNull() {
            assertInvalid("hours.missing", () -> new Hours(null));
        }
    }

    @Nested
    @DisplayName("hours.positive")
    class Positive {

        @ParameterizedTest(name = "[{index}] {0} -> hours.positive")
        @ValueSource(strings = {"0", "0.00", "-0.25", "-8.00"})
        @DisplayName("weigert nul en negatieve uren")
        void rejectsZeroAndNegative(String value) {
            // KARAKTERISERING: 0 uur is ongeldig. Een correctieboeking naar nul is
            // dus niet mogelijk; de regel moet worden verwijderd. Zie docs/testing.md.
            assertInvalid("hours.positive", () -> new Hours(new BigDecimal(value)));
        }
    }

    @Nested
    @DisplayName("hours.step")
    class Step {

        @ParameterizedTest(name = "[{index}] {0} -> hours.step")
        @ValueSource(strings = {"0.10", "0.20", "0.30", "1.10", "7.99", "8.01"})
        @DisplayName("eist veelvouden van een kwartier")
        void rejectsNonQuarter(String value) {
            assertInvalid("hours.step", () -> new Hours(new BigDecimal(value)));
        }

        @ParameterizedTest(name = "[{index}] {0} is toegestaan")
        @ValueSource(strings = {"0.25", "0.50", "0.75", "1.00", "7.75", "40.00"})
        @DisplayName("accepteert veelvouden van een kwartier")
        void acceptsQuarters(String value) {
            assertAccepted(() -> new Hours(new BigDecimal(value)));
        }

        @Test
        @DisplayName("de controle kijkt naar de waarde, niet naar het aantal decimalen")
        void stepIsValueBased() {
            // KARAKTERISERING: 0.2500 heeft scale 4 maar is een geldig kwartier.
            // De stapcontrole gebruikt remainder, niet de schaal.
            assertAccepted(() -> new Hours(new BigDecimal("0.2500")));
        }
    }

    @Nested
    @DisplayName("of(String)")
    class Parsing {

        @Test
        @DisplayName("parseert een geldige tekstwaarde")
        void parsesValid() {
            assertThat(Hours.of("7.50").value()).isEqualByComparingTo("7.50");
        }

        @Test
        @DisplayName("laat de regelcodes van de constructor door")
        void propagatesViolations() {
            assertInvalid("hours.step", () -> Hours.of("0.10"));
            assertInvalid("hours.positive", () -> Hours.of("0"));
        }

        @Test
        @DisplayName("een niet-numerieke waarde geeft geen domeinfout maar NumberFormatException")
        void nonNumericThrowsNumberFormat() {
            // KARAKTERISERING: of() valideert het formaat niet zelf. BigDecimal gooit
            // NumberFormatException, geen BusinessRuleViolation. De REST-laag vangt
            // dat af als IllegalArgumentException -> HTTP 400. Een agent die op
            // rule-codes vertrouwt, ziet hier dus geen code.
            assertThatThrownBy(() -> Hours.of("acht"))
                    .isInstanceOf(NumberFormatException.class)
                    .isNotInstanceOf(BusinessRuleViolation.class);
        }
    }

    @Nested
    @DisplayName("normalisatie")
    class Normalisation {

        @ParameterizedTest(name = "[{index}] {0} -> scale 2")
        @ValueSource(strings = {"1", "1.0", "1.00", "0.25", "0.2500"})
        @DisplayName("zet de scale altijd op twee")
        void alwaysScaleTwo(String value) {
            assertThat(new Hours(new BigDecimal(value)).value().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("uren met verschillende schrijfwijze zijn gelijk")
        void equalityAfterNormalisation() {
            assertThat(Hours.of("8")).isEqualTo(Hours.of("8.00"));
        }
    }

    @Nested
    @DisplayName("plus")
    class Addition {

        @ParameterizedTest(name = "[{index}] {0} + {1} = {2}")
        @CsvSource({"7.50, 0.50, 8.00", "0.25, 0.25, 0.50", "40.00, 0.25, 40.25"})
        @DisplayName("telt op en blijft binnen de kwartierstap")
        void adds(String left, String right, String expected) {
            assertThat(Hours.of(left).plus(Hours.of(right)).value()).isEqualByComparingTo(expected);
        }
    }

    @Nested
    @DisplayName("vergelijken")
    class Comparison {

        @ParameterizedTest(name = "[{index}] {0} > {1} is {2}")
        @CsvSource({"8.00, 7.75, true", "7.75, 8.00, false", "8.00, 8.00, false"})
        @DisplayName("isGreaterThan vergelijkt op waarde")
        void isGreaterThan(String left, String right, boolean expected) {
            assertThat(Hours.of(left).isGreaterThan(Hours.of(right))).isEqualTo(expected);
        }

        @Test
        @DisplayName("compareTo maakt sorteren mogelijk en negeert de schaal")
        void compareTo() {
            assertThat(Hours.of("8.00")).isEqualByComparingTo(Hours.of("8"));
            assertThat(Hours.of("0.25")).isLessThan(Hours.of("0.50"));
            assertThat(Hours.of("40.00")).isGreaterThan(Hours.of("0.25"));
        }
    }

    @Nested
    @DisplayName("JpaConverter")
    class Converter {

        private final Hours.JpaConverter converter = new Hours.JpaConverter();

        @Test
        @DisplayName("bewaart de waarde en leest hem identiek terug")
        void roundTrip() {
            Hours original = Hours.of("7.50");
            BigDecimal column = converter.convertToDatabaseColumn(original);

            assertThat(column).isEqualByComparingTo("7.50");
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
            assertInvalid("hours.step", () -> converter.convertToEntityAttribute(new BigDecimal("0.10")));
        }
    }
}
