package nl.haystaq.tijdwijs.personeel.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import nl.haystaq.tijdwijs.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link ContractType}. Patroon: zie {@code IbanTest}. */
@DisplayName("ContractType")
class ContractTypeTest {

    private static Money euro(String amount) {
        return Money.of(new BigDecimal(amount));
    }

    @Nested
    @DisplayName("parse")
    class Parse {

        @ParameterizedTest
        @NullSource
        @DisplayName("weigert een ontbrekende waarde")
        void rejectsNull(String raw) {
            assertInvalid("contract_type.missing", () -> ContractType.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> contract_type.unknown")
        @ValueSource(strings = {"", "VAST", "ZZP", "PERMANENTE", "PERM", "  "})
        @DisplayName("weigert een onbekende waarde")
        void rejectsUnknown(String raw) {
            assertInvalid("contract_type.unknown", () -> ContractType.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(ContractType.class)
        @DisplayName("herkent elke bestaande waarde")
        void parsesAllValues(ContractType type) {
            assertThat(ContractType.parse(type.name())).isEqualTo(type);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
            "permanent,     PERMANENT",
            "PeRmAnEnT,     PERMANENT",
            "' PERMANENT ', PERMANENT",
            "'\tfreelance', FREELANCE"
        })
        @DisplayName("trimt en negeert kapitalisatie")
        void trimsAndIgnoresCase(String raw, ContractType expected) {
            assertThat(ContractType.parse(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("intern.rate")
    class InternRate {

        @ParameterizedTest(name = "[{index}] INTERN met {0} -> intern.rate")
        @ValueSource(strings = {"45.01", "46.00", "95.00"})
        @DisplayName("weigert een tarief boven 45,00 voor een stagiair")
        void rejectsAboveMaximum(String rate) {
            assertInvalid("intern.rate", () -> ContractType.INTERN.validateRate(euro(rate)));
        }

        @ParameterizedTest(name = "[{index}] INTERN met {0} is toegestaan")
        @ValueSource(strings = {"0.00", "44.99", "45.00"})
        @DisplayName("staat 45,00 en lager toe, inclusief de grens")
        void acceptsUpToMaximum(String rate) {
            assertAccepted(() -> ContractType.INTERN.validateRate(euro(rate)));
        }
    }

    @Nested
    @DisplayName("freelance.rate")
    class FreelanceRate {

        @ParameterizedTest(name = "[{index}] FREELANCE met {0} -> freelance.rate")
        @ValueSource(strings = {"0.00", "45.00", "59.99"})
        @DisplayName("weigert een tarief onder 60,00 voor een freelancer")
        void rejectsBelowMinimum(String rate) {
            assertInvalid("freelance.rate", () -> ContractType.FREELANCE.validateRate(euro(rate)));
        }

        @ParameterizedTest(name = "[{index}] FREELANCE met {0} is toegestaan")
        @ValueSource(strings = {"60.00", "60.01", "250.00"})
        @DisplayName("staat 60,00 en hoger toe, inclusief de grens")
        void acceptsFromMinimum(String rate) {
            assertAccepted(() -> ContractType.FREELANCE.validateRate(euro(rate)));
        }
    }

    @Nested
    @DisplayName("contractvormen zonder tarieflimiet")
    class Unrestricted {

        @ParameterizedTest(name = "[{index}] {0} met 0,00 en met 9999,00")
        @EnumSource(
                value = ContractType.class,
                names = {"PERMANENT", "TEMPORARY"})
        @DisplayName("PERMANENT en TEMPORARY kennen geen boven- of ondergrens")
        void noLimits(ContractType type) {
            // KARAKTERISERING: alleen INTERN en FREELANCE hebben een tarieflimiet.
            // Deze afspraken staan niet in de functionele documentatie.
            assertAccepted(() -> type.validateRate(euro("0.00")));
            assertAccepted(() -> type.validateRate(euro("9999.00")));
        }
    }

    @Nested
    @DisplayName("validateRate met null")
    class NullRate {

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(
                value = ContractType.class,
                names = {"PERMANENT", "TEMPORARY"})
        @DisplayName("zonder limiet wordt een ontbrekend tarief niet gecontroleerd")
        void unrestrictedIgnoresNull(ContractType type) {
            // KARAKTERISERING: validateRate raakt hourlyRate alleen aan voor INTERN
            // en FREELANCE. Voor de andere vormen glipt null er ongemerkt door; de
            // controle op een ontbrekend tarief zit in EmployeeService
            // (hourly_rate.missing), niet hier.
            assertAccepted(() -> type.validateRate(null));
        }

        @ParameterizedTest(name = "[{index}] {0} met null")
        @EnumSource(
                value = ContractType.class,
                names = {"INTERN", "FREELANCE"})
        @DisplayName("met limiet leidt een ontbrekend tarief tot een NullPointerException")
        void restrictedThrowsOnNull(ContractType type) {
            // KARAKTERISERING: geen BusinessRuleViolation maar een NPE. Een agent
            // ziet hier dus geen rule-code. Vastgelegd, niet gerepareerd.
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> type.validateRate(null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
