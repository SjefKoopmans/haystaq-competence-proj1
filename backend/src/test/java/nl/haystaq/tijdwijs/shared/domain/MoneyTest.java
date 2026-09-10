package nl.haystaq.tijdwijs.shared.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link Money}. Patroon: zie {@code IbanTest}. */
@DisplayName("Money")
class MoneyTest {

    @Nested
    @DisplayName("money.missing")
    class Missing {

        @Test
        @DisplayName("de constructor weigert null")
        void constructorRejectsNull() {
            assertInvalid("money.missing", () -> new Money(null));
        }

        @Test
        @DisplayName("of() weigert null")
        void ofRejectsNull() {
            assertInvalid("money.missing", () -> Money.of(null));
        }
    }

    @Nested
    @DisplayName("money.scale")
    class Scale {

        @ParameterizedTest(name = "[{index}] {0} -> money.scale")
        @ValueSource(strings = {"1.234", "0.001", "95.000"})
        @DisplayName("de constructor weigert meer dan twee decimalen")
        void constructorRejectsTooManyDecimals(String value) {
            assertInvalid("money.scale", () -> new Money(new BigDecimal(value)));
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({"1.234, 1.23", "1.235, 1.24", "1.236, 1.24", "0.005, 0.01"})
        @DisplayName("of() rondt af op twee decimalen in plaats van te weigeren")
        void ofRoundsHalfUp(String input, String expected) {
            // KARAKTERISERING: of() en de constructor gedragen zich verschillend bij
            // meer dan twee decimalen. of() rondt af (HALF_UP), de constructor gooit
            // money.scale. Twee ingangen, twee uitkomsten.
            assertThat(Money.of(new BigDecimal(input)).amount()).isEqualByComparingTo(expected);
        }
    }

    @Nested
    @DisplayName("money.negative")
    class Negative {

        @ParameterizedTest(name = "[{index}] {0} -> money.negative")
        @ValueSource(strings = {"-0.01", "-1.00", "-9999.99"})
        @DisplayName("weigert negatieve bedragen")
        void rejectsNegative(String value) {
            assertInvalid("money.negative", () -> new Money(new BigDecimal(value)));
        }

        @Test
        @DisplayName("of() weigert negatief ook na afronding")
        void ofRejectsNegative() {
            assertInvalid("money.negative", () -> Money.of(new BigDecimal("-1.234")));
        }
    }

    @Nested
    @DisplayName("geldige invoer")
    class Accepted {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"0.00", "0", "0.5", "95.00", "10000.00"})
        @DisplayName("accepteert nul en positieve bedragen tot twee decimalen")
        void acceptsValid(String value) {
            assertAccepted(() -> new Money(new BigDecimal(value)));
        }

        @Test
        @DisplayName("nul is toegestaan, in tegenstelling tot Hours")
        void zeroIsAllowed() {
            // KARAKTERISERING: Money staat 0 toe (signum >= 0), Hours niet
            // (hours.positive eist > 0). Bewust verschil tussen de twee VO's.
            assertAccepted(() -> new Money(BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("normalisatie")
    class Normalisation {

        @ParameterizedTest(name = "[{index}] {0} -> scale 2")
        @ValueSource(strings = {"0", "5", "5.5", "95.00"})
        @DisplayName("zet de scale altijd op twee")
        void alwaysScaleTwo(String value) {
            assertThat(new Money(new BigDecimal(value)).amount().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("ZERO is 0.00 met scale twee")
        void zeroConstant() {
            assertThat(Money.ZERO.amount()).isEqualByComparingTo("0.00");
            assertThat(Money.ZERO.amount().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("bedragen met verschillende schrijfwijze zijn gelijk")
        void equalityAfterNormalisation() {
            assertThat(new Money(new BigDecimal("5"))).isEqualTo(new Money(new BigDecimal("5.00")));
        }
    }

    @Nested
    @DisplayName("isGreaterThan")
    class Comparison {

        @ParameterizedTest(name = "[{index}] {0} > {1} is {2}")
        @CsvSource({
            "95.00, 45.00, true",
            "45.00, 95.00, false",
            "45.00, 45.00, false",
            "45.01, 45.00, true",
            "0.00,  0.00,  false"
        })
        @DisplayName("vergelijkt op waarde, niet op schaal")
        void comparesByValue(String left, String right, boolean expected) {
            Money a = new Money(new BigDecimal(left));
            Money b = new Money(new BigDecimal(right));
            assertThat(a.isGreaterThan(b)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("JpaConverter")
    class Converter {

        private final Money.JpaConverter converter = new Money.JpaConverter();

        @Test
        @DisplayName("bewaart de waarde en leest hem identiek terug")
        void roundTrip() {
            Money original = Money.of(new BigDecimal("95.00"));
            BigDecimal column = converter.convertToDatabaseColumn(original);

            assertThat(column).isEqualByComparingTo("95.00");
            assertThat(converter.convertToEntityAttribute(column)).isEqualTo(original);
        }

        @Test
        @DisplayName("null blijft null in beide richtingen")
        void nullSafe() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("een negatieve waarde uit de database wordt geweigerd")
        void validatesOnRead() {
            assertInvalid("money.negative", () -> converter.convertToEntityAttribute(new BigDecimal("-1.00")));
        }
    }
}
