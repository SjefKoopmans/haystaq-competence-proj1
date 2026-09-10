package nl.haystaq.tijdwijs.projecten.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link ProjectStatus}. Patroon: zie {@code IbanTest}. */
@DisplayName("ProjectStatus")
class ProjectStatusTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("null wordt DRAFT in plaats van een fout")
        void nullBecomesDraft() {
            // KARAKTERISERING: anders dan ContractType.parse (contract_type.missing)
            // gooit deze parse geen fout bij null, maar kiest stilzwijgend DRAFT.
            // Inconsistent tussen de twee enums. Zie docs/testing.md.
            assertThat(ProjectStatus.parse(null)).isEqualTo(ProjectStatus.DRAFT);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> status.unknown")
        @ValueSource(strings = {"", "CONCEPT", "ACTIEF", "ONHOLD", "ON HOLD", "  "})
        @DisplayName("weigert een onbekende waarde")
        void rejectsUnknown(String raw) {
            assertInvalid("status.unknown", () -> ProjectStatus.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(ProjectStatus.class)
        @DisplayName("herkent elke bestaande waarde")
        void parsesAllValues(ProjectStatus status) {
            assertThat(ProjectStatus.parse(status.name())).isEqualTo(status);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({"active, ACTIVE", "AcTiVe, ACTIVE", "' ACTIVE ', ACTIVE", "on_hold, ON_HOLD"})
        @DisplayName("trimt en negeert kapitalisatie")
        void trimsAndIgnoresCase(String raw, ProjectStatus expected) {
            assertThat(ProjectStatus.parse(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("allowedNext")
    class AllowedTransitions {

        @ParameterizedTest(name = "[{index}] {0} -> {1} is toegestaan")
        @CsvSource({
            "DRAFT,   DRAFT",
            "DRAFT,   ACTIVE",
            "ACTIVE,  ACTIVE",
            "ACTIVE,  ON_HOLD",
            "ACTIVE,  CLOSED",
            "ON_HOLD, ON_HOLD",
            "ON_HOLD, ACTIVE",
            "ON_HOLD, CLOSED",
            "CLOSED,  CLOSED"
        })
        @DisplayName("staat de toegestane overgangen toe")
        void allowsTransition(ProjectStatus from, ProjectStatus to) {
            assertThat(from.allowedNext()).contains(to);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1} is niet toegestaan")
        @CsvSource({
            "DRAFT,   ON_HOLD",
            "DRAFT,   CLOSED",
            "ACTIVE,  DRAFT",
            "ON_HOLD, DRAFT",
            "CLOSED,  DRAFT",
            "CLOSED,  ACTIVE",
            "CLOSED,  ON_HOLD"
        })
        @DisplayName("blokkeert de overige overgangen")
        void blocksTransition(ProjectStatus from, ProjectStatus to) {
            assertThat(from.allowedNext()).doesNotContain(to);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(ProjectStatus.class)
        @DisplayName("elke status staat zichzelf toe, zodat opnieuw opslaan mag")
        void selfTransitionAlwaysAllowed(ProjectStatus status) {
            assertThat(status.allowedNext()).contains(status);
        }

        @Test
        @DisplayName("CLOSED is een eindtoestand")
        void closedIsTerminal() {
            assertThat(ProjectStatus.CLOSED.allowedNext()).containsExactly(ProjectStatus.CLOSED);
        }

        @Test
        @DisplayName("DRAFT is niet meer bereikbaar zodra het project verder is")
        void draftIsUnreachable() {
            // KARAKTERISERING: alleen DRAFT zelf staat DRAFT toe. Een project kan
            // dus nooit terug naar concept.
            assertThat(ProjectStatus.ACTIVE.allowedNext()).doesNotContain(ProjectStatus.DRAFT);
            assertThat(ProjectStatus.ON_HOLD.allowedNext()).doesNotContain(ProjectStatus.DRAFT);
            assertThat(ProjectStatus.CLOSED.allowedNext()).doesNotContain(ProjectStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("allowsBooking")
    class Booking {

        @Test
        @DisplayName("alleen ACTIVE staat boeken toe")
        void onlyActiveAllowsBooking() {
            assertThat(ProjectStatus.ACTIVE.allowsBooking()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} staat boeken niet toe")
        @EnumSource(
                value = ProjectStatus.class,
                names = {"DRAFT", "ON_HOLD", "CLOSED"})
        @DisplayName("de overige statussen blokkeren boeken")
        void othersBlockBooking(ProjectStatus status) {
            assertThat(status.allowsBooking()).isFalse();
        }
    }
}
