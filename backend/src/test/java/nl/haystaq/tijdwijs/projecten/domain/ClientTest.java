package nl.haystaq.tijdwijs.projecten.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor het aggregate {@link Client}. Patroon: zie {@code IbanTest}. */
@DisplayName("Client")
class ClientTest {

    private static Client register() {
        return Client.register("Gemeente Zandvliet", "info@zandvliet.nl", "NL123456789B01", "NL", 30, true);
    }

    @Nested
    @DisplayName("name")
    class Name {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("weigert een ontbrekende naam; de rule-code is de veldnaam")
        void rejectsMissingName(String name) {
            assertInvalid("name", () -> Client.register(name, null, null, null, null, null));
        }

        @Test
        @DisplayName("weigert een naam van meer dan 120 tekens")
        void rejectsTooLongName() {
            assertInvalid("name", () -> Client.register("a".repeat(121), null, null, null, null, null));
        }

        @Test
        @DisplayName("accepteert exact 120 tekens en trimt de waarde")
        void acceptsBoundaryAndTrims() {
            assertAccepted(() -> Client.register("a".repeat(120), null, null, null, null, null));

            Client client = Client.register("  Gemeente  ", null, null, null, null, null);
            assertThat(client.name()).isEqualTo("Gemeente");
        }
    }

    @Nested
    @DisplayName("contact_email")
    class ContactEmail {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> contact_email")
        @ValueSource(strings = {"", "geen-apenstaartje", "@zandvliet.nl", "info@", "info@zandvliet"})
        @DisplayName("weigert een onjuist e-mailadres")
        void rejectsMalformed(String email) {
            assertInvalid("contact_email", () -> Client.register("Naam", email, null, null, null, null));
        }

        @Test
        @DisplayName("een ontbrekend e-mailadres is toegestaan")
        void nullIsOptional() {
            assertAccepted(() -> Client.register("Naam", null, null, null, null, null));
        }

        @Test
        @DisplayName("bewaart het adres ongewijzigd")
        void keepsValueAsIs() {
            // KARAKTERISERING: anders dan EmailAddress wordt hier niet genormaliseerd
            // naar kleine letters. Client gebruikt een eigen patroon in plaats van
            // het value object.
            Client client = Client.register("Naam", "Info@Zandvliet.NL", null, null, null, null);

            assertThat(client.contactEmail()).isEqualTo("Info@Zandvliet.NL");
        }
    }

    @Nested
    @DisplayName("vat_number")
    class VatNumber {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> vat_number")
        @ValueSource(
                strings = {
                    "",
                    "NL12345678", // acht tekens na de landcode
                    "NL1234567890123", // dertien tekens na de landcode
                    "nl123456789B01", // kleine letters
                    "N1123456789B01", // cijfer in de landcode
                    "NL123456789B0!" // ongeldig teken
                })
        @DisplayName("weigert een onjuist btw-nummer")
        void rejectsMalformed(String vat) {
            assertInvalid("vat_number", () -> Client.register("Naam", null, vat, null, null, null));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" is toegestaan")
        @ValueSource(strings = {"NL123456789", "NL123456789B01", "BE0123456789", "DE123456789012"})
        @DisplayName("accepteert negen tot twaalf tekens na de landcode")
        void acceptsValid(String vat) {
            assertAccepted(() -> Client.register("Naam", null, vat, null, null, null));
        }

        @Test
        @DisplayName("een ontbrekend btw-nummer is toegestaan")
        void nullIsOptional() {
            assertAccepted(() -> Client.register("Naam", null, null, null, null, null));
        }
    }

    @Nested
    @DisplayName("country")
    class Country {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> country")
        @ValueSource(strings = {"", "N", "NLD", "N1", "12"})
        @DisplayName("eist een landcode van twee letters")
        void rejectsMalformed(String country) {
            assertInvalid("country", () -> Client.register("Naam", null, null, country, null, null));
        }

        @Test
        @DisplayName("een ontbrekende landcode wordt NL")
        void nullBecomesNl() {
            assertThat(Client.register("Naam", null, null, null, null, null).country()).isEqualTo("NL");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({"be, BE", "De, DE", "NL, NL"})
        @DisplayName("maakt hoofdletters van de landcode")
        void uppercases(String input, String expected) {
            assertThat(Client.register("Naam", null, null, input, null, null).country())
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("payment_term_days")
    class PaymentTerm {

        @ParameterizedTest(name = "[{index}] {0} dagen -> payment_term_days")
        @ValueSource(ints = {-1, 121, 365})
        @DisplayName("eist een termijn tussen nul en 120 dagen")
        void rejectsOutsideRange(int term) {
            assertInvalid("payment_term_days", () -> Client.register("Naam", null, null, null, term, null));
        }

        @ParameterizedTest(name = "[{index}] {0} dagen is toegestaan")
        @ValueSource(ints = {0, 30, 60, 120})
        @DisplayName("accepteert de grenzen van het bereik")
        void acceptsBoundaries(int term) {
            assertAccepted(() -> Client.register("Naam", null, null, null, term, null));
        }

        @Test
        @DisplayName("een ontbrekende termijn wordt 30 dagen")
        void nullBecomes30() {
            assertThat((int) Client.register("Naam", null, null, null, null, null).paymentTermDays())
                    .isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("register: resultaat")
    class Result {

        @Test
        @DisplayName("legt een opdrachtgever vast met een eigen id")
        void registersClient() {
            Client client = register();

            assertThat(client.id()).isNotNull();
            assertThat(client.name()).isEqualTo("Gemeente Zandvliet");
            assertThat(client.contactEmail()).isEqualTo("info@zandvliet.nl");
            assertThat(client.vatNumber()).isEqualTo("NL123456789B01");
            assertThat(client.country()).isEqualTo("NL");
            assertThat((int) client.paymentTermDays()).isEqualTo(30);
            assertThat(client.isActive()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] active={0} wordt {1}")
        @CsvSource(
                value = {"NULL, true", "true, true", "false, false"},
                nullValues = "NULL")
        @DisplayName("een ontbrekende actief-vlag betekent actief")
        void nullActiveMeansTrue(Boolean active, boolean expected) {
            // KARAKTERISERING: `active == null || active`, dus null wordt true.
            // Bij Absence.request geldt het omgekeerde: daar wordt null false.
            assertThat(Client.register("Naam", null, null, null, null, active).isActive())
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("elke registratie krijgt een nieuw id")
        void generatesUniqueIds() {
            assertThat(register().id()).isNotEqualTo(register().id());
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("zet de opdrachtgever op inactief")
        void deactivates() {
            Client client = register();

            client.deactivate();

            assertThat(client.isActive()).isFalse();
        }

        @Test
        @DisplayName("dubbel deactiveren is toegestaan en er is geen weg terug")
        void deactivationIsIrreversible() {
            // KARAKTERISERING: er is geen activate()-methode en geen update. Een
            // gedeactiveerde opdrachtgever is alleen via de database te herstellen.
            Client client = register();
            client.deactivate();

            assertAccepted(client::deactivate);
            assertThat(client.isActive()).isFalse();
        }
    }
}
