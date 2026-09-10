package nl.haystaq.tijdwijs.urenregistratie.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link EntryType}. Patroon: zie {@code IbanTest}. */
@DisplayName("EntryType")
class EntryTypeTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("null wordt REGULAR in plaats van een fout")
        void nullBecomesRegular() {
            // KARAKTERISERING: stilzwijgende default, net als ProjectStatus.parse
            // en Role.parse. ContractType.parse en Absence.Type.parse gooien juist
            // wel een fout bij null. Inconsistent; zie docs/testing.md.
            assertThat(EntryType.parse(null)).isEqualTo(EntryType.REGULAR);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> entry_type.unknown")
        @ValueSource(strings = {"", "NORMAAL", "OVERWORK", "OVER_TIME", "REIS", "  "})
        @DisplayName("weigert een onbekende waarde")
        void rejectsUnknown(String raw) {
            assertInvalid("entry_type.unknown", () -> EntryType.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(EntryType.class)
        @DisplayName("herkent elke bestaande waarde")
        void parsesAllValues(EntryType type) {
            assertThat(EntryType.parse(type.name())).isEqualTo(type);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
            "overtime,     OVERTIME",
            "OvErTiMe,     OVERTIME",
            "' OVERTIME ', OVERTIME",
            "'\ttravel',   TRAVEL",
            "standby,      STANDBY",
            "training,     TRAINING"
        })
        @DisplayName("trimt en negeert kapitalisatie")
        void trimsAndIgnoresCase(String raw, EntryType expected) {
            assertThat(EntryType.parse(raw)).isEqualTo(expected);
        }
    }
}
