package nl.haystaq.tijdwijs.urenregistratie.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertConflict;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import nl.haystaq.tijdwijs.shared.domain.Hours;
import nl.haystaq.tijdwijs.shared.domain.IsoWeek;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor het aggregate {@link Timesheet} en de entiteit
 * {@link TimeEntry}. Patroon: zie {@code IbanTest}.
 *
 * <p>Deze klasse staat in het pakket {@code urenregistratie.domain} omdat de
 * constructor van {@code TimeEntry} package-private is. Regels van {@code
 * TimeEntry} worden getest via {@code Timesheet.book}, zoals de
 * aggregate-grens voorschrijft.
 *
 * <p>Week 2026-W06 loopt van maandag 2 februari tot en met zondag 8 februari,
 * gelijk aan de seed-data.
 */
@DisplayName("Timesheet")
class TimesheetTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID TASK_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID PROJECT_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final IsoWeek WEEK = new IsoWeek(2026, 6);
    private static final LocalDate MONDAY = LocalDate.of(2026, 2, 2);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 2, 8);
    private static final Hours FULL_TIME = Hours.of("40.00");

    private static Timesheet draft() {
        return Timesheet.open(EMPLOYEE_ID, WEEK);
    }

    /** Boekt declarabele reguliere uren; alleen dag en aantal variëren. */
    private static TimeEntry book(Timesheet timesheet, LocalDate date, String hours) {
        return timesheet.book(
                TASK_ID, PROJECT_ID, date, Hours.of(hours), EntryType.REGULAR, "Analyse", true, FULL_TIME);
    }

    /** Vult de week met 40 uur, zodat indienen en overwerk mogelijk zijn. */
    private static Timesheet fullWeek() {
        Timesheet timesheet = draft();
        for (int day = 0; day < 5; day++) {
            book(timesheet, MONDAY.plusDays(day), "8.00");
        }
        return timesheet;
    }

    private static Timesheet submitted() {
        Timesheet timesheet = fullWeek();
        timesheet.submit(FULL_TIME, BigDecimal.ZERO, null);
        return timesheet;
    }

    @Nested
    @DisplayName("open")
    class Open {

        @Test
        @DisplayName("opent een weekstaat als concept")
        void opensAsDraft() {
            Timesheet timesheet = draft();

            assertThat(timesheet.id()).isNotNull();
            assertThat(timesheet.employeeId()).isEqualTo(EMPLOYEE_ID);
            assertThat(timesheet.week()).isEqualTo(WEEK);
            assertThat(timesheet.status()).isEqualTo(TimesheetStatus.DRAFT);
            assertThat(timesheet.entries()).isEmpty();
            assertThat(timesheet.submittedAt()).isNull();
            assertThat(timesheet.approvedAt()).isNull();
            assertThat(timesheet.approvedBy()).isNull();
        }

        @Test
        @DisplayName("weigert een ontbrekende medewerker")
        void rejectsMissingEmployee() {
            assertInvalid("employee_id.missing", () -> Timesheet.open(null, WEEK));
        }

        @Test
        @DisplayName("weigert een ontbrekende week")
        void rejectsMissingWeek() {
            assertInvalid("week.missing", () -> Timesheet.open(EMPLOYEE_ID, null));
        }

        @Test
        @DisplayName("elke weekstaat krijgt een nieuw id")
        void generatesUniqueIds() {
            assertThat(draft().id()).isNotEqualTo(draft().id());
        }
    }

    @Nested
    @DisplayName("book: timesheet.locked")
    class BookLocked {

        @Test
        @DisplayName("weigert boeken op een ingediende weekstaat")
        void rejectsBookingOnSubmitted() {
            Timesheet timesheet = submitted();

            assertConflict("timesheet.locked", () -> book(timesheet, MONDAY, "1.00"));
        }

        @Test
        @DisplayName("weigert boeken op een goedgekeurde weekstaat")
        void rejectsBookingOnApproved() {
            Timesheet timesheet = submitted();
            timesheet.approve(UUID.randomUUID(), true);

            assertConflict("timesheet.locked", () -> book(timesheet, MONDAY, "1.00"));
        }

        @Test
        @DisplayName("staat boeken toe op een afgekeurde weekstaat")
        void allowsBookingOnRejected() {
            // KARAKTERISERING: na afkeuring gaat de weekstaat weer open, zodat de
            // medewerker kan corrigeren.
            Timesheet timesheet = submitted();
            timesheet.reject("Onjuiste taak geboekt");

            assertAccepted(() -> book(timesheet, MONDAY, "1.00"));
        }
    }

    @Nested
    @DisplayName("book: work_date.outside_week")
    class BookOutsideWeek {

        @ParameterizedTest(name = "[{index}] {0} valt buiten 2026-W06")
        @ValueSource(strings = {"2026-02-01", "2026-02-09", "2026-01-26", "2026-03-02"})
        @DisplayName("weigert een datum buiten de week")
        void rejectsDateOutsideWeek(LocalDate date) {
            Timesheet timesheet = draft();

            assertInvalid("work_date.outside_week", () -> book(timesheet, date, "8.00"));
        }

        @ParameterizedTest(name = "[{index}] {0} valt binnen 2026-W06")
        @ValueSource(strings = {"2026-02-02", "2026-02-05", "2026-02-08"})
        @DisplayName("staat maandag tot en met zondag toe")
        void allowsDatesWithinWeek(LocalDate date) {
            Timesheet timesheet = draft();

            assertAccepted(() -> book(timesheet, date, "8.00"));
        }

        @Test
        @DisplayName("een ontbrekende datum geeft een NullPointerException, geen rule-code")
        void nullDateThrowsNpe() {
            // KARAKTERISERING: week.contains(null) gooit een NPE vóórdat
            // work_date.missing in TimeEntry wordt bereikt. Een agent ziet hier dus
            // geen code. Zie docs/testing.md.
            Timesheet timesheet = draft();

            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> book(timesheet, null, "8.00")))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("book: day.max_hours")
    class BookDayMaximum {

        @Test
        @DisplayName("weigert meer dan 16 uur op één dag")
        void rejectsAbove16Hours() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");
            book(timesheet, MONDAY, "8.00");

            assertConflict("day.max_hours", () -> book(timesheet, MONDAY, "0.25"));
        }

        @Test
        @DisplayName("staat exact 16 uur op één dag toe")
        void allowsExactly16Hours() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");

            assertAccepted(() -> book(timesheet, MONDAY, "8.00"));
            assertThat(timesheet.totalOn(MONDAY)).isEqualByComparingTo("16.00");
        }

        @Test
        @DisplayName("de dagteller staat per dag los")
        void countsPerDay() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "12.00");

            assertAccepted(() -> book(timesheet, MONDAY.plusDays(1), "12.00"));
        }

        @Test
        @DisplayName("de dagcontrole komt vóór de regelcontrole op 12 uur")
        void dayCheckBeforeEntryCheck() {
            // KARAKTERISERING: één regel mag maximaal 12 uur (hours.max), maar de
            // dagcontrole in het aggregate slaat als eerste toe.
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");

            assertConflict("day.max_hours", () -> book(timesheet, MONDAY, "12.00"));
        }
    }

    @Nested
    @DisplayName("book: overtime.before_contract_hours")
    class BookOvertime {

        @Test
        @DisplayName("weigert overwerk voordat de contracturen vol zijn")
        void rejectsOvertimeBeforeContractHours() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");

            assertConflict(
                    "overtime.before_contract_hours",
                    () -> timesheet.book(
                            TASK_ID,
                            PROJECT_ID,
                            MONDAY.plusDays(1),
                            Hours.of("2.00"),
                            EntryType.OVERTIME,
                            "Uitloop",
                            true,
                            FULL_TIME));
        }

        @Test
        @DisplayName("staat overwerk toe zodra de contracturen gehaald zijn")
        void allowsOvertimeAfterContractHours() {
            Timesheet timesheet = fullWeek();

            assertAccepted(() -> timesheet.book(
                    TASK_ID, PROJECT_ID, SUNDAY, Hours.of("2.00"), EntryType.OVERTIME, "Uitloop", true, FULL_TIME));
        }

        @ParameterizedTest(name = "[{index}] {0} kent geen contractureneis")
        @EnumSource(
                value = EntryType.class,
                names = {"REGULAR", "TRAVEL", "STANDBY", "TRAINING"})
        @DisplayName("alleen OVERTIME wordt op contracturen gecontroleerd")
        void onlyOvertimeIsChecked(EntryType entryType) {
            Timesheet timesheet = draft();

            assertAccepted(() -> timesheet.book(
                    TASK_ID, PROJECT_ID, MONDAY, Hours.of("2.00"), entryType, "Toelichting", true, FULL_TIME));
        }

        @Test
        @DisplayName("bij een deeltijdcontract is overwerk eerder mogelijk")
        void partTimeReachesContractHoursSooner() {
            Timesheet timesheet = draft();
            Hours partTime = Hours.of("8.00");
            timesheet.book(
                    TASK_ID, PROJECT_ID, MONDAY, Hours.of("8.00"), EntryType.REGULAR, "Analyse", true, partTime);

            assertAccepted(() -> timesheet.book(
                    TASK_ID,
                    PROJECT_ID,
                    MONDAY.plusDays(1),
                    Hours.of("2.00"),
                    EntryType.OVERTIME,
                    "Uitloop",
                    true,
                    partTime));
        }
    }

    @Nested
    @DisplayName("book: regels van TimeEntry")
    class BookEntryRules {

        @Test
        @DisplayName("weigert een ontbrekende taak")
        void rejectsMissingTask() {
            Timesheet timesheet = draft();

            assertInvalid(
                    "task_id.missing",
                    () -> timesheet.book(
                            null, PROJECT_ID, MONDAY, Hours.of("8.00"), EntryType.REGULAR, "Analyse", true, FULL_TIME));
        }

        @Test
        @DisplayName("weigert meer dan 12 uur op één regel")
        void rejectsAbove12HoursPerEntry() {
            Timesheet timesheet = draft();

            assertInvalid("hours.max", () -> book(timesheet, MONDAY, "12.25"));
        }

        @Test
        @DisplayName("staat exact 12 uur op één regel toe")
        void allowsExactly12HoursPerEntry() {
            Timesheet timesheet = draft();

            assertAccepted(() -> book(timesheet, MONDAY, "12.00"));
        }

        @Test
        @DisplayName("weigert een toelichting van meer dan 500 tekens")
        void rejectsTooLongDescription() {
            Timesheet timesheet = draft();
            String tooLong = "a".repeat(501);

            assertInvalid(
                    "description.length",
                    () -> timesheet.book(
                            TASK_ID,
                            PROJECT_ID,
                            MONDAY,
                            Hours.of("8.00"),
                            EntryType.REGULAR,
                            tooLong,
                            true,
                            FULL_TIME));
        }

        @Test
        @DisplayName("accepteert exact 500 tekens")
        void allowsExactly500Characters() {
            Timesheet timesheet = draft();

            assertAccepted(() -> timesheet.book(
                    TASK_ID,
                    PROJECT_ID,
                    MONDAY,
                    Hours.of("8.00"),
                    EntryType.REGULAR,
                    "a".repeat(500),
                    true,
                    FULL_TIME));
        }

        @ParameterizedTest(name = "[{index}] toelichting \"{0}\" bij niet-declarabel")
        @CsvSource(
                value = {"NULL", "''", "'  '", "'ab'", "'  a  '"},
                nullValues = "NULL")
        @DisplayName("eist een toelichting van drie tekens bij niet-declarabele uren")
        void requiresDescriptionWhenNotBillable(String description) {
            Timesheet timesheet = draft();

            assertInvalid(
                    "description.required_non_billable",
                    () -> timesheet.book(
                            TASK_ID,
                            PROJECT_ID,
                            MONDAY,
                            Hours.of("8.00"),
                            EntryType.REGULAR,
                            description,
                            false,
                            FULL_TIME));
        }

        @Test
        @DisplayName("accepteert niet-declarabele uren met toelichting")
        void allowsNonBillableWithDescription() {
            Timesheet timesheet = draft();

            assertAccepted(() -> timesheet.book(
                    TASK_ID,
                    PROJECT_ID,
                    MONDAY,
                    Hours.of("1.00"),
                    EntryType.REGULAR,
                    "Weekstart",
                    false,
                    FULL_TIME));
        }

        @Test
        @DisplayName("declarabele uren mogen zonder toelichting")
        void allowsBillableWithoutDescription() {
            Timesheet timesheet = draft();

            assertAccepted(() -> timesheet.book(
                    TASK_ID, PROJECT_ID, MONDAY, Hours.of("8.00"), EntryType.REGULAR, null, true, FULL_TIME));
        }

        @Test
        @DisplayName("een ontbrekend project wordt niet gecontroleerd")
        void missingProjectIsNotValidated() {
            // KARAKTERISERING: projectId kent geen require, terwijl de kolom in
            // V1__schema.sql wel NOT NULL is. Het aggregate laat het door; de
            // database weigert het pas bij opslaan. Zie docs/testing.md.
            Timesheet timesheet = draft();

            assertAccepted(() -> timesheet.book(
                    TASK_ID, null, MONDAY, Hours.of("8.00"), EntryType.REGULAR, "Analyse", true, FULL_TIME));
        }
    }

    @Nested
    @DisplayName("book: resultaat")
    class BookResult {

        @Test
        @DisplayName("levert de nieuwe regel op en voegt hem toe")
        void returnsAndAddsEntry() {
            Timesheet timesheet = draft();

            TimeEntry entry = book(timesheet, MONDAY, "6.50");

            assertThat(entry.id()).isNotNull();
            assertThat(entry.workDate()).isEqualTo(MONDAY);
            assertThat(entry.hours()).isEqualTo(Hours.of("6.50"));
            assertThat(timesheet.entries()).containsExactly(entry);
        }

        @Test
        @DisplayName("entries() geeft een onwijzigbare kopie")
        void entriesIsImmutableCopy() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");

            assertThat(timesheet.entries()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("removeEntry")
    class RemoveEntry {

        @Test
        @DisplayName("verwijdert een bestaande regel")
        void removesEntry() {
            Timesheet timesheet = draft();
            TimeEntry entry = book(timesheet, MONDAY, "8.00");

            timesheet.removeEntry(entry.id());

            assertThat(timesheet.entries()).isEmpty();
        }

        @Test
        @DisplayName("weigert een onbekende regel")
        void rejectsUnknownEntry() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");

            assertConflict("entry.missing", () -> timesheet.removeEntry(UUID.randomUUID()));
        }

        @Test
        @DisplayName("weigert verwijderen uit een ingediende weekstaat")
        void rejectsRemovalWhenLocked() {
            Timesheet timesheet = submitted();
            UUID entryId = timesheet.entries().get(0).id();

            assertConflict("timesheet.locked", () -> timesheet.removeEntry(entryId));
        }

        @Test
        @DisplayName("de slotcontrole komt vóór de bestaanscontrole")
        void lockedCheckComesFirst() {
            Timesheet timesheet = submitted();

            assertConflict("timesheet.locked", () -> timesheet.removeEntry(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("dient een volledige week in")
        void submitsFullWeek() {
            Timesheet timesheet = fullWeek();

            timesheet.submit(FULL_TIME, BigDecimal.ZERO, "Graag akkoord");

            assertThat(timesheet.status()).isEqualTo(TimesheetStatus.SUBMITTED);
            assertThat(timesheet.submittedAt()).isNotNull();
            assertThat(timesheet.comment()).isEqualTo("Graag akkoord");
        }

        @Test
        @DisplayName("weigert indienen zonder regels")
        void rejectsEmptyTimesheet() {
            Timesheet timesheet = draft();

            assertConflict("submit.no_entries", () -> timesheet.submit(FULL_TIME, BigDecimal.ZERO, null));
        }

        @Test
        @DisplayName("weigert indienen als de contracturen niet gehaald zijn")
        void rejectsIncompleteWeek() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");

            assertConflict("submit.week_incomplete", () -> timesheet.submit(FULL_TIME, BigDecimal.ZERO, null));
        }

        @Test
        @DisplayName("goedgekeurd verlof telt mee voor de dekking")
        void approvedAbsenceCountsTowardsCoverage() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "8.00");
            book(timesheet, MONDAY.plusDays(1), "8.00");
            book(timesheet, MONDAY.plusDays(2), "8.00");
            book(timesheet, MONDAY.plusDays(3), "8.00");

            assertAccepted(() -> timesheet.submit(FULL_TIME, new BigDecimal("8.00"), null));
            assertThat(timesheet.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        }

        @Test
        @DisplayName("weigert indienen van een al ingediende weekstaat")
        void rejectsResubmission() {
            Timesheet timesheet = submitted();

            assertConflict("status.not_submittable", () -> timesheet.submit(FULL_TIME, BigDecimal.ZERO, null));
        }

        @Test
        @DisplayName("staat opnieuw indienen na afkeuring toe")
        void allowsResubmissionAfterRejection() {
            Timesheet timesheet = submitted();
            timesheet.reject("Onjuiste taak geboekt");

            assertAccepted(() -> timesheet.submit(FULL_TIME, BigDecimal.ZERO, null));
            assertThat(timesheet.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        }

        @Test
        @DisplayName("de statuscontrole komt vóór de regelcontrole")
        void statusCheckComesFirst() {
            Timesheet timesheet = submitted();

            assertConflict("status.not_submittable", () -> timesheet.submit(FULL_TIME, BigDecimal.ZERO, null));
        }

        @Test
        @DisplayName("een ontbrekend verlofsaldo geeft een NullPointerException")
        void nullAbsenceHoursThrowsNpe() {
            // KARAKTERISERING: approvedAbsenceHours wordt zonder null-controle
            // opgeteld. De applicatielaag moet BigDecimal.ZERO doorgeven.
            Timesheet timesheet = fullWeek();

            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> timesheet.submit(FULL_TIME, null, null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        private final UUID approver = UUID.randomUUID();

        @Test
        @DisplayName("keurt een ingediende weekstaat goed")
        void approvesSubmitted() {
            Timesheet timesheet = submitted();

            timesheet.approve(approver, true);

            assertThat(timesheet.status()).isEqualTo(TimesheetStatus.APPROVED);
            assertThat(timesheet.approvedBy()).isEqualTo(approver);
            assertThat(timesheet.approvedAt()).isNotNull();
        }

        @ParameterizedTest(name = "[{index}] goedkeuren vanuit {0}")
        @EnumSource(
                value = TimesheetStatus.class,
                names = {"DRAFT", "APPROVED", "REJECTED"})
        @DisplayName("weigert goedkeuren vanuit elke andere status")
        void rejectsFromOtherStatuses(TimesheetStatus status) {
            Timesheet timesheet = timesheetIn(status);

            assertConflict("status.not_approvable", () -> timesheet.approve(approver, true));
        }

        @Test
        @DisplayName("weigert een ontbrekende goedkeurder")
        void rejectsMissingApprover() {
            Timesheet timesheet = submitted();

            assertInvalid("approver.missing", () -> timesheet.approve(null, true));
        }

        @Test
        @DisplayName("weigert goedkeuren door de medewerker zelf")
        void rejectsSelfApproval() {
            Timesheet timesheet = submitted();

            assertConflict("approver.self", () -> timesheet.approve(EMPLOYEE_ID, true));
        }

        @Test
        @DisplayName("weigert een goedkeurder zonder bevoegdheid")
        void rejectsUnauthorisedApprover() {
            Timesheet timesheet = submitted();

            assertConflict("approver.not_authorised", () -> timesheet.approve(approver, false));
        }

        @Test
        @DisplayName("de statuscontrole komt vóór de goedkeurderscontroles")
        void statusCheckComesFirst() {
            Timesheet timesheet = draft();

            assertConflict("status.not_approvable", () -> timesheet.approve(null, false));
        }

        @Test
        @DisplayName("zelfgoedkeuring wint van ontbrekende bevoegdheid")
        void selfCheckBeforeAuthorisation() {
            Timesheet timesheet = submitted();

            assertConflict("approver.self", () -> timesheet.approve(EMPLOYEE_ID, false));
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("keurt een ingediende weekstaat af met reden")
        void rejectsWithReason() {
            Timesheet timesheet = submitted();

            timesheet.reject("Onjuiste taak geboekt");

            assertThat(timesheet.status()).isEqualTo(TimesheetStatus.REJECTED);
            assertThat(timesheet.comment()).isEqualTo("Onjuiste taak geboekt");
        }

        @ParameterizedTest(name = "[{index}] afkeuren vanuit {0}")
        @EnumSource(
                value = TimesheetStatus.class,
                names = {"DRAFT", "APPROVED", "REJECTED"})
        @DisplayName("weigert afkeuren vanuit elke andere status")
        void rejectsFromOtherStatuses(TimesheetStatus status) {
            Timesheet timesheet = timesheetIn(status);

            assertConflict("status.not_rejectable", () -> timesheet.reject("Onjuiste taak"));
        }

        @ParameterizedTest(name = "[{index}] reden \"{0}\" -> comment.required")
        @CsvSource(
                value = {"NULL", "''", "'    '", "'kort'", "'  ab  '"},
                nullValues = "NULL")
        @DisplayName("eist een reden van minstens vijf tekens na trimmen")
        void requiresReason(String reason) {
            Timesheet timesheet = submitted();

            assertInvalid("comment.required", () -> timesheet.reject(reason));
        }

        @Test
        @DisplayName("accepteert exact vijf tekens")
        void acceptsExactlyFiveCharacters() {
            Timesheet timesheet = submitted();

            assertAccepted(() -> timesheet.reject("fout!"));
        }

        @Test
        @DisplayName("de statuscontrole komt vóór de redencontrole")
        void statusCheckComesFirst() {
            Timesheet timesheet = draft();

            assertConflict("status.not_rejectable", () -> timesheet.reject(null));
        }
    }

    @Nested
    @DisplayName("totalen")
    class Totals {

        @Test
        @DisplayName("een lege weekstaat heeft nul uren")
        void emptyTimesheetIsZero() {
            Timesheet timesheet = draft();

            assertThat(timesheet.totalHours()).isEqualByComparingTo("0.00");
            assertThat(timesheet.totalOn(MONDAY)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("telt de uren per dag op")
        void sumsPerDay() {
            Timesheet timesheet = draft();
            book(timesheet, MONDAY, "6.50");
            book(timesheet, MONDAY, "1.00");
            book(timesheet, MONDAY.plusDays(1), "8.00");

            assertThat(timesheet.totalOn(MONDAY)).isEqualByComparingTo("7.50");
            assertThat(timesheet.totalOn(MONDAY.plusDays(1))).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("telt de uren over de hele week op")
        void sumsWholeWeek() {
            Timesheet timesheet = fullWeek();

            assertThat(timesheet.totalHours()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("een dag zonder boekingen is nul, ook buiten de week")
        void unknownDayIsZero() {
            Timesheet timesheet = fullWeek();

            assertThat(timesheet.totalOn(SUNDAY)).isEqualByComparingTo("0.00");
            assertThat(timesheet.totalOn(LocalDate.of(2020, 1, 1))).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("een totaal mag nul zijn, een boeking niet")
        void totalMayBeZeroUnlikeHours() {
            // KARAKTERISERING: totalen zijn BigDecimal en niet Hours, juist omdat
            // Hours nul verbiedt (hours.positive).
            assertThat(draft().totalHours()).isEqualByComparingTo(BigDecimal.ZERO);
            assertInvalid("hours.positive", () -> Hours.of("0.00"));
        }
    }

    /** Brengt een weekstaat in de gevraagde status, voor de statusmatrixtests. */
    private static Timesheet timesheetIn(TimesheetStatus status) {
        return switch (status) {
            case DRAFT -> draft();
            case SUBMITTED -> submitted();
            case APPROVED -> {
                Timesheet timesheet = submitted();
                timesheet.approve(UUID.randomUUID(), true);
                yield timesheet;
            }
            case REJECTED -> {
                Timesheet timesheet = submitted();
                timesheet.reject("Onjuiste taak geboekt");
                yield timesheet;
            }
        };
    }
}
