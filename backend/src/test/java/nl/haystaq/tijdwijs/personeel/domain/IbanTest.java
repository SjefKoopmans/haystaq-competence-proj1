package nl.haystaq.tijdwijs.personeel.domain;

import nl.haystaq.tijdwijs.shared.domain.BusinessRuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rooktest voor de surefire-configuratie (F0.3) en tegelijk het patroon voor de
 * karakteriseringstests van F0.4: elke afwijzing asserteert op
 * {@link BusinessRuleViolation#code()}, zodat de test vastlegt <em>welke</em>
 * regel is geraakt en niet alleen dat er iets faalde.
 */
class IbanTest {

    @Test
    void normalisesSpacesAndCase() {
        assertThat(new Iban("nl91 abna 0417 1643 00").value()).isEqualTo("NL91ABNA0417164300");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'',                    iban.format",
            "XX00,                  iban.format",
            "NL91ABNA041716430,     iban.nl_length",
            "NL91ABNA0417164301,    iban.mod97"
    })
    void rejectsInvalidIbanWithSpecificCode(String value, String expectedCode) {
        assertThatThrownBy(() -> new Iban(value))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting(ex -> ((BusinessRuleViolation) ex).code())
                .isEqualTo(expectedCode);
    }

    @Test
    void rejectsNullWithMissingCode() {
        assertThatThrownBy(() -> new Iban(null))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting(ex -> ((BusinessRuleViolation) ex).code())
                .isEqualTo("iban.missing");
    }
}
