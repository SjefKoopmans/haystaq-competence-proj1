package nl.haystaq.tijdwijs.urenregistratie.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import nl.haystaq.tijdwijs.urenregistratie.domain.Absence.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor het aggregate {@link Absence}. Patroon: zie
 * {@code IbanTest}.
 *
 * <p><strong>Alle datums zijn relatief aan vandaag.</strong> {@code
 * sick.retroactive} vergelijkt met {@code LocalDate.now()} en er is geen
 * {@code Clock} te injecteren.
 */
@DisplayName("Absence")
class AbsenceTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final LocalDate TODAY = LocalDate.now();

    private static Absence request(Type type, LocalDate start, LocalDate end) {
        return Absence.request(EMPLOYEE_ID, type, start, end, null, null, null);
    }

    private static Absence approvedVacation(LocalDate start, LocalDate end, String hoursPerDay) {
        return Absence.request(
                EMPLOYEE_ID, Type.VACATION, start, end, new BigDecimal(hoursPerDay), true, null);
    }

    @Nested
    @DisplayName("Type.parse")
    class TypeParsing {

        @ParameterizedTest
        @NullSource
        @DisplayName("weigert een ontbrekende waarde")
        void rejectsNull(String raw) {
            // KARAKTERISERING: anders dan EntryType.parse en ProjectStatus.parse
            // gooit deze parse wel een fout bij null.
            assertInvalid("absence_type.missing", () -> Type.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> absence_type.unknown")
        @ValueSource(strings = {"", "VAKANTIE", "ZIEK", "HOLIDAY", "  "})
        @DisplayName("weigert een onbekende waarde")
        void rejectsUnknown(String raw) {
            assertInvalid("absence_type.unknown", () -> Type.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(Type.class)
        @DisplayName("herkent elke bestaande waarde")
        void parsesAllValues(Type type) {
            assertThat(Type.parse(type.name())).isEqualTo(type);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({"vacation, VACATION", "VaCaTiOn, VACATION", "' SICK ', SICK", "parental, PARENTAL"})
        @DisplayName("trimt en negeert kapitalisatie")
        void trimsAndIgnoresCase(String raw, Type expected) {
            assertThat(Type.parse(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("request: verplichte velden")
    class RequiredFields {

        @Test
        @DisplayName("weigert een ontbrekende medewerker")
        void rejectsMissingEmployee() {
            assertInvalid(
                    "employee_id.missing",
                    () -> Absence.request(null, Type.VACATION, TODAY, TODAY, null, null, null));
        }

        @Test
        @DisplayName("weigert een ontbrekende begindatum")
        void rejectsMissingStartDate() {
            assertInvalid("date.missing", () -> request(Type.VACATION, null, TODAY));
        }

        @Test
        @DisplayName("weigert een ontbrekende einddatum")
        void rejectsMissingEndDate() {
            assertInvalid("date.missing", () -> request(Type.VACATION, TODAY, null));
        }

        @Test
        @DisplayName("een ontbrekend type wordt niet gecontroleerd")
        void missingTypeIsNotValidated() {
            // KARAKTERISERING: request() controleert het type niet op null, terwijl
            // de kolom NOT NULL is. Type.parse doet dat wel, maar wordt hier niet
            // aangeroepen. Zie docs/testing.md.
            assertAccepted(() -> request(null, TODAY, TODAY));
        }
    }

    @Nested
    @DisplayName("request: date.order")
    class DateOrder {

        @ParameterizedTest(name = "[{index}] einddatum {0} dagen vóór begindatum")
        @ValueSource(longs = {1, 5, 100})
        @DisplayName("weigert een einddatum vóór de begindatum")
        void rejectsEndBeforeStart(long daysBefore) {
            assertInvalid("date.order", () -> request(Type.VACATION, TODAY, TODAY.minusDays(daysBefore)));
        }

        @Test
        @DisplayName("staat één dag verlof toe")
        void allowsSingleDay() {
            assertAccepted(() -> request(Type.VACATION, TODAY, TODAY));
        }
    }

    @Nested
    @DisplayName("request: duration.max")
    class Duration {

        @ParameterizedTest(name = "[{index}] {0} dagen tussen begin en eind -> duration.max")
        @ValueSource(longs = {61, 90, 365})
        @DisplayName("weigert een periode van meer dan 60 dagen")
        void rejectsTooLongPeriod(long days) {
            assertInvalid("duration.max", () -> request(Type.VACATION, TODAY, TODAY.plusDays(days)));
        }

        @ParameterizedTest(name = "[{index}] {0} dagen is toegestaan")
        @ValueSource(longs = {0, 30, 60})
        @DisplayName("staat 60 dagen toe, inclusief de grens")
        void acceptsUpTo60Days(long days) {
            // KARAKTERISERING: de grens is 60 dagen verschil, dus 61 kalenderdagen
            // verlof.
            assertAccepted(() -> request(Type.VACATION, TODAY, TODAY.plusDays(days)));
        }
    }

    @Nested
    @DisplayName("request: hours_per_day")
    class HoursPerDay {

        @ParameterizedTest(name = "[{index}] {0} uur -> hours_per_day.range")
        @ValueSource(strings = {"0", "0.00", "-1.00", "8.50", "9.00", "24.00"})
        @DisplayName("eist meer dan nul en maximaal acht uur")
        void rejectsOutsideRange(String hours) {
            assertInvalid(
                    "hours_per_day.range",
                    () -> Absence.request(
                            EMPLOYEE_ID, Type.VACATION, TODAY, TODAY, new BigDecimal(hours), null, null));
        }

        @ParameterizedTest(name = "[{index}] {0} uur -> hours_per_day.step")
        @ValueSource(strings = {"0.25", "1.75", "7.25"})
        @DisplayName("eist stappen van een half uur")
        void rejectsNonHalfHourSteps(String hours) {
            // KARAKTERISERING: verlof gaat per half uur, terwijl uren boeken per
            // kwartier kan (Hours). Derde stapgrootte in dit domein, naast
            // contract_hours.step.
            assertInvalid(
                    "hours_per_day.step",
                    () -> Absence.request(
                            EMPLOYEE_ID, Type.VACATION, TODAY, TODAY, new BigDecimal(hours), null, null));
        }

        @ParameterizedTest(name = "[{index}] {0} uur is toegestaan")
        @ValueSource(strings = {"0.50", "4.00", "7.50", "8.00"})
        @DisplayName("accepteert halve uren tot en met acht")
        void acceptsHalfHours(String hours) {
            assertAccepted(() -> Absence.request(
                    EMPLOYEE_ID, Type.VACATION, TODAY, TODAY, new BigDecimal(hours), null, null));
        }

        @Test
        @DisplayName("een ontbrekende waarde wordt een volledige dag van acht uur")
        void nullBecomesFullDay() {
            assertThat(request(Type.VACATION, TODAY, TODAY).hoursPerDay()).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("de bereikcontrole komt vóór de stapcontrole")
        void rangeCheckComesFirst() {
            assertInvalid(
                    "hours_per_day.range",
                    () -> Absence.request(
                            EMPLOYEE_ID, Type.VACATION, TODAY, TODAY, new BigDecimal("8.25"), null, null));
        }
    }

    @Nested
    @DisplayName("request: sick.retroactive")
    class SickRetroactive {

        @ParameterizedTest(name = "[{index}] ziekmelding {0} dagen terug -> sick.retroactive")
        @ValueSource(longs = {15, 30, 90})
        @DisplayName("weigert een ziekmelding van meer dan veertien dagen terug")
        void rejectsTooLateSickReport(long daysAgo) {
            LocalDate start = TODAY.minusDays(daysAgo);

            assertInvalid("sick.retroactive", () -> request(Type.SICK, start, start));
        }

        @ParameterizedTest(name = "[{index}] ziekmelding {0} dagen terug is toegestaan")
        @ValueSource(longs = {0, 1, 14})
        @DisplayName("staat een ziekmelding tot veertien dagen terug toe")
        void acceptsRecentSickReport(long daysAgo) {
            LocalDate start = TODAY.minusDays(daysAgo);

            assertAccepted(() -> request(Type.SICK, start, start));
        }

        @Test
        @DisplayName("een ziekmelding in de toekomst is toegestaan")
        void allowsFutureSickReport() {
            // KARAKTERISERING: de regel begrenst alleen terugwerkende kracht. Een
            // ziekmelding vooruit boeken mag, want het verschil is dan negatief.
            assertAccepted(() -> request(Type.SICK, TODAY.plusDays(30), TODAY.plusDays(30)));
        }

        @ParameterizedTest(name = "[{index}] {0} kent geen terugwerkende grens")
        @EnumSource(
                value = Type.class,
                names = {"VACATION", "PARENTAL", "UNPAID"})
        @DisplayName("alleen SICK wordt op terugwerkende kracht gecontroleerd")
        void onlySickIsChecked(Type type) {
            LocalDate start = TODAY.minusDays(90);

            assertAccepted(() -> request(type, start, start));
        }
    }

    @Nested
    @DisplayName("request: special.reason_required")
    class SpecialReason {

        @ParameterizedTest(name = "[{index}] reden \"{0}\" -> special.reason_required")
        @CsvSource(
                value = {"NULL", "''", "'   '"},
                nullValues = "NULL")
        @DisplayName("eist een reden bij bijzonder verlof")
        void requiresReasonForSpecialLeave(String reason) {
            assertInvalid(
                    "special.reason_required",
                    () -> Absence.request(EMPLOYEE_ID, Type.SPECIAL, TODAY, TODAY, null, null, reason));
        }

        @Test
        @DisplayName("accepteert bijzonder verlof met reden")
        void acceptsSpecialLeaveWithReason() {
            assertAccepted(() -> Absence.request(EMPLOYEE_ID, Type.SPECIAL, TODAY, TODAY, null, null, "Verhuizing"));
        }

        @Test
        @DisplayName("één teken is al genoeg als reden")
        void singleCharacterSuffices() {
            // KARAKTERISERING: alleen isBlank wordt gecontroleerd, geen minimumlengte.
            // Timesheet.reject eist wel vijf tekens (comment.required).
            assertAccepted(() -> Absence.request(EMPLOYEE_ID, Type.SPECIAL, TODAY, TODAY, null, null, "x"));
        }

        @ParameterizedTest(name = "[{index}] {0} kent geen redeneis")
        @EnumSource(
                value = Type.class,
                names = {"VACATION", "SICK", "PARENTAL", "UNPAID"})
        @DisplayName("alleen SPECIAL vereist een reden")
        void onlySpecialRequiresReason(Type type) {
            assertAccepted(() -> request(type, TODAY, TODAY));
        }
    }

    @Nested
    @DisplayName("request: resultaat")
    class Result {

        @Test
        @DisplayName("legt een verlofaanvraag vast met een eigen id")
        void createsAbsence() {
            Absence absence = request(Type.VACATION, TODAY, TODAY.plusDays(4));

            assertThat(absence.id()).isNotNull();
            assertThat(absence.employeeId()).isEqualTo(EMPLOYEE_ID);
            assertThat(absence.absenceType()).isEqualTo(Type.VACATION);
            assertThat(absence.startDate()).isEqualTo(TODAY);
            assertThat(absence.endDate()).isEqualTo(TODAY.plusDays(4));
            assertThat(absence.isApproved()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] approved={0} wordt {1}")
        @CsvSource(
                value = {"NULL, false", "false, false", "true, true"},
                nullValues = "NULL")
        @DisplayName("een ontbrekende goedkeuring geldt als niet goedgekeurd")
        void nullApprovedMeansFalse(Boolean approved, boolean expected) {
            Absence absence = Absence.request(EMPLOYEE_ID, Type.VACATION, TODAY, TODAY, null, approved, null);

            assertThat(absence.isApproved()).isEqualTo(expected);
        }

        @Test
        @DisplayName("een aanvraag kan direct goedgekeurd worden aangemaakt")
        void mayBeCreatedApproved() {
            // KARAKTERISERING: request() staat approved=true toe zonder enige
            // bevoegdheidscontrole. Anders dan Timesheet.approve, dat approver en
            // authorised eist.
            assertThat(approvedVacation(TODAY, TODAY, "8.00").isApproved()).isTrue();
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("keurt een aanvraag goed")
        void approves() {
            Absence absence = request(Type.VACATION, TODAY, TODAY);

            absence.approve();

            assertThat(absence.isApproved()).isTrue();
        }

        @Test
        @DisplayName("dubbel goedkeuren is toegestaan")
        void doubleApprovalAllowed() {
            // KARAKTERISERING: approve() heeft geen statuscontrole en geen
            // goedkeurder. Vergelijk Timesheet.approve, dat vier regels bewaakt
            // (status.not_approvable, approver.missing, approver.self,
            // approver.not_authorised). Vermoedelijk een ontbrekende invariant.
            Absence absence = request(Type.VACATION, TODAY, TODAY);
            absence.approve();

            assertAccepted(absence::approve);
            assertThat(absence.isApproved()).isTrue();
        }
    }

    @Nested
    @DisplayName("overlaps")
    class Overlaps {

        private final Absence absence = request(Type.VACATION, TODAY.plusDays(10), TODAY.plusDays(14));

        @ParameterizedTest(name = "[{index}] venster [{0},{1}] overlapt: {2}")
        @CsvSource({
            "8,  9,  false", // volledig ervoor
            "15, 16, false", // volledig erna
            "9,  10, true", // raakt de begindatum
            "14, 15, true", // raakt de einddatum
            "11, 12, true", // volledig binnen
            "1,  30, true", // omvat de hele periode
            "10, 14, true" // exact gelijk
        })
        @DisplayName("is inclusief aan beide kanten")
        void inclusiveOnBothEnds(long fromOffset, long toOffset, boolean expected) {
            assertThat(absence.overlaps(TODAY.plusDays(fromOffset), TODAY.plusDays(toOffset)))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("een enkele dag overlapt met zichzelf")
        void singleDayOverlapsItself() {
            Absence single = request(Type.VACATION, TODAY, TODAY);

            assertThat(single.overlaps(TODAY, TODAY)).isTrue();
        }
    }

    @Nested
    @DisplayName("blocksFullDay")
    class BlocksFullDay {

        @Test
        @DisplayName("blokkeert een dag bij goedgekeurd verlof van acht uur")
        void blocksOnApprovedFullDay() {
            Absence absence = approvedVacation(TODAY, TODAY.plusDays(2), "8.00");

            assertThat(absence.blocksFullDay(TODAY)).isTrue();
            assertThat(absence.blocksFullDay(TODAY.plusDays(2))).isTrue();
        }

        @Test
        @DisplayName("blokkeert niet buiten de periode")
        void doesNotBlockOutsidePeriod() {
            Absence absence = approvedVacation(TODAY, TODAY.plusDays(2), "8.00");

            assertThat(absence.blocksFullDay(TODAY.minusDays(1))).isFalse();
            assertThat(absence.blocksFullDay(TODAY.plusDays(3))).isFalse();
        }

        @Test
        @DisplayName("blokkeert niet zonder goedkeuring")
        void doesNotBlockWhenNotApproved() {
            Absence absence = Absence.request(
                    EMPLOYEE_ID, Type.VACATION, TODAY, TODAY, new BigDecimal("8.00"), false, null);

            assertThat(absence.blocksFullDay(TODAY)).isFalse();
        }

        @ParameterizedTest(name = "[{index}] {0} uur blokkeert: {1}")
        @CsvSource({"0.50, false", "4.00, false", "7.50, false", "8.00, true"})
        @DisplayName("blokkeert alleen bij een volledige dag van acht uur")
        void onlyBlocksOnFullDay(String hoursPerDay, boolean expected) {
            Absence absence = approvedVacation(TODAY, TODAY, hoursPerDay);

            assertThat(absence.blocksFullDay(TODAY)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("hoursWithin")
    class HoursWithin {

        @Test
        @DisplayName("telt de uren per dag binnen het venster")
        void sumsHoursPerDay() {
            Absence absence = approvedVacation(TODAY, TODAY.plusDays(4), "8.00");

            assertThat(absence.hoursWithin(TODAY, TODAY.plusDays(4))).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("kapt af op de grenzen van het venster")
        void clipsToWindow() {
            Absence absence = approvedVacation(TODAY, TODAY.plusDays(9), "8.00");

            assertThat(absence.hoursWithin(TODAY.plusDays(2), TODAY.plusDays(4))).isEqualByComparingTo("24.00");
        }

        @Test
        @DisplayName("kapt af als het verlof vóór het venster begint")
        void clipsWhenStartingBeforeWindow() {
            Absence absence = approvedVacation(TODAY.minusDays(5), TODAY.plusDays(1), "8.00");

            assertThat(absence.hoursWithin(TODAY, TODAY.plusDays(6))).isEqualByComparingTo("16.00");
        }

        @Test
        @DisplayName("geeft nul zonder goedkeuring")
        void zeroWhenNotApproved() {
            Absence absence = request(Type.VACATION, TODAY, TODAY.plusDays(4));

            assertThat(absence.hoursWithin(TODAY, TODAY.plusDays(4))).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("geeft nul buiten het venster")
        void zeroOutsideWindow() {
            Absence absence = approvedVacation(TODAY.plusDays(10), TODAY.plusDays(12), "8.00");

            assertThat(absence.hoursWithin(TODAY, TODAY.plusDays(5))).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("rekent met halve dagen")
        void handlesHalfDays() {
            Absence absence = approvedVacation(TODAY, TODAY.plusDays(4), "4.00");

            assertThat(absence.hoursWithin(TODAY, TODAY.plusDays(4))).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("telt kalenderdagen, geen werkdagen")
        void countsCalendarDays() {
            // KARAKTERISERING: weekenden en feestdagen worden niet overgeslagen.
            // Twee weken verlof levert 14 x 8 = 112 uur op, niet 80.
            Absence absence = approvedVacation(TODAY, TODAY.plusDays(13), "8.00");

            assertThat(absence.hoursWithin(TODAY, TODAY.plusDays(13))).isEqualByComparingTo("112.00");
        }
    }
}
