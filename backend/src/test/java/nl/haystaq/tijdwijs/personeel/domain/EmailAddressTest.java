package nl.haystaq.tijdwijs.personeel.domain;

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

/** Karakteriseringstests voor {@link EmailAddress}. Patroon: zie {@code IbanTest}. */
@DisplayName("EmailAddress")
class EmailAddressTest {

    @Nested
    @DisplayName("email.length")
    class Length {

        @ParameterizedTest
        @NullSource
        @DisplayName("weigert een ontbrekende waarde met de lengtecode")
        void rejectsNull(String value) {
            // KARAKTERISERING: null en een te lange waarde delen dezelfde code.
            // Er is geen aparte email.missing.
            assertInvalid("email.length", () -> new EmailAddress(value));
        }

        @Test
        @DisplayName("weigert meer dan 160 tekens")
        void rejectsTooLong() {
            String local = "a".repeat(150);
            assertInvalid("email.length", () -> new EmailAddress(local + "@haystaq.nl")); // 161
        }

        @Test
        @DisplayName("accepteert exact 160 tekens")
        void acceptsExactly160() {
            String value = "a".repeat(149) + "@haystaq.nl"; // 149 + 11 = 160
            assertThat(value).hasSize(160);
            assertAccepted(() -> new EmailAddress(value));
        }
    }

    @Nested
    @DisplayName("email.format")
    class Format {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> email.format")
        @ValueSource(
                strings = {
                    "",
                    "geen-apenstaartje",
                    "@haystaq.nl", // geen lokaal deel
                    "sanne@", // geen domein
                    "sanne@haystaq", // geen punt in het domein
                    "sanne@haystaq.n", // topleveldomein te kort
                    "sanne@@haystaq.nl", // twee apenstaartjes
                    "sanne de wit@haystaq.nl", // ruimte in het lokale deel
                    "sanne@haystaq .nl", // ruimte in het domein
                    "sanne@.haystaq.nl" // punt direct na het apenstaartje
                })
        @DisplayName("weigert waarden die niet aan het patroon voldoen")
        void rejectsMalformed(String value) {
            assertInvalid("email.format", () -> new EmailAddress(value));
        }

        @Test
        @DisplayName("de lengtecontrole komt vóór de formaatcontrole")
        void lengthCheckWins() {
            assertInvalid("email.length", () -> new EmailAddress("x".repeat(200)));
        }
    }

    @Nested
    @DisplayName("geldige invoer")
    class Accepted {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(
                strings = {
                    "sanne.de.wit@haystaq.nl",
                    "a@b.co",
                    "joost+werk@haystaq.nl",
                    "j.o.o.s.t@haystaq.nl",
                    "joost_bakker@haystaq.nl",
                    "sanne@sub.haystaq.nl"
                })
        @DisplayName("accepteert gangbare adressen")
        void acceptsValid(String value) {
            assertAccepted(() -> new EmailAddress(value));
        }

        @Test
        @DisplayName("accepteert een subdomein")
        void acceptsSubdomain() {
            // KARAKTERISERING: het patroon staat geen punt toe in het eerste
            // domeinlabel, maar wel erna. sub.haystaq.nl matcht dus wel.
            assertAccepted(() -> new EmailAddress("sanne@sub.haystaq.nl"));
        }
    }

    @Nested
    @DisplayName("normalisatie")
    class Normalisation {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
            "Sanne.De.Wit@Haystaq.NL, sanne.de.wit@haystaq.nl",
            "SANNE@HAYSTAQ.NL,        sanne@haystaq.nl",
            "sanne@haystaq.nl,        sanne@haystaq.nl"
        })
        @DisplayName("maakt kleine letters")
        void lowercases(String input, String expected) {
            assertThat(new EmailAddress(input).value()).isEqualTo(expected);
        }

        @Test
        @DisplayName("twee schrijfwijzen van hetzelfde adres zijn gelijk")
        void equalityIgnoresCase() {
            assertThat(new EmailAddress("Sanne@Haystaq.NL")).isEqualTo(new EmailAddress("sanne@haystaq.nl"));
        }

        @Test
        @DisplayName("toString geeft de genormaliseerde waarde")
        void toStringReturnsValue() {
            assertThat(new EmailAddress("SANNE@HAYSTAQ.NL")).hasToString("sanne@haystaq.nl");
        }

        @Test
        @DisplayName("witruimte wordt niet getrimd")
        void doesNotTrim() {
            // KARAKTERISERING: anders dan Iban wordt hier geen witruimte gestript.
            // Een adres met voorloopruimte faalt op het formaat.
            assertInvalid("email.format", () -> new EmailAddress(" sanne@haystaq.nl"));
        }
    }

    @Nested
    @DisplayName("JpaConverter")
    class Converter {

        private final EmailAddress.JpaConverter converter = new EmailAddress.JpaConverter();

        @Test
        @DisplayName("bewaart de waarde en leest hem identiek terug")
        void roundTrip() {
            EmailAddress original = new EmailAddress("sanne@haystaq.nl");
            String column = converter.convertToDatabaseColumn(original);

            assertThat(column).isEqualTo("sanne@haystaq.nl");
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
            assertInvalid("email.format", () -> converter.convertToEntityAttribute("kapot"));
        }
    }
}
