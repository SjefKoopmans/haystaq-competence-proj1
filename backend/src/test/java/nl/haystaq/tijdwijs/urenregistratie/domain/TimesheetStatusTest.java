package nl.haystaq.tijdwijs.urenregistratie.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Karakteriseringstests voor {@link TimesheetStatus}.
 *
 * <p>Deze enum kent geen rule-codes en ook geen {@code parse}. De statuscodes
 * (`status.not_submittable` en verwanten) zitten in {@code Timesheet}; zie
 * {@code TimesheetTest}.
 */
@DisplayName("TimesheetStatus")
class TimesheetStatusTest {

    @Nested
    @DisplayName("isEditable")
    class Editable {

        @ParameterizedTest(name = "[{index}] {0} is bewerkbaar")
        @EnumSource(
                value = TimesheetStatus.class,
                names = {"DRAFT", "REJECTED"})
        @DisplayName("een concept of afgekeurde weekstaat is bewerkbaar")
        void draftAndRejectedAreEditable(TimesheetStatus status) {
            assertThat(status.isEditable()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} is niet bewerkbaar")
        @EnumSource(
                value = TimesheetStatus.class,
                names = {"SUBMITTED", "APPROVED"})
        @DisplayName("een ingediende of goedgekeurde weekstaat is op slot")
        void submittedAndApprovedAreLocked(TimesheetStatus status) {
            assertThat(status.isEditable()).isFalse();
        }

        @Test
        @DisplayName("een afgekeurde weekstaat kan opnieuw worden bewerkt")
        void rejectedReopensForEditing() {
            // KARAKTERISERING: REJECTED is geen eindtoestand. Na afkeuring mag de
            // medewerker corrigeren en opnieuw indienen.
            assertThat(TimesheetStatus.REJECTED.isEditable()).isTrue();
        }
    }
}
