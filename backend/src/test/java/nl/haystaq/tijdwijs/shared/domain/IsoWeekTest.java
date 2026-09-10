package nl.haystaq.tijdwijs.shared.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor {@link IsoWeek}. Patroon: zie {@code IbanTest}.
 *
 * <p>ISO-8601 is contra-intuïtief: week 1 is de week waarin 4 januari valt, dus
 * 1 januari kan tot week 52 of 53 van het vórige jaar horen. De grensgevallen
 * hieronder zijn met opzet gekozen rond die jaarovergang.
 */
@DisplayName("IsoWeek")
class IsoWeekTest {

    @Nested
    @DisplayName("iso_year.range")
    class YearRange {

        @ParameterizedTest(name = "[{index}] jaar {0} -> iso_year.range")
        @ValueSource(ints = {1999, 1990, 0, -1, 2101, 3000})
        @DisplayName("eist een jaar tussen 2000 en 2100")
        void rejectsYearOutsideRange(int year) {
            assertInvalid("iso_year.range", () -> new IsoWeek(year, 1));
        }

        @ParameterizedTest(name = "[{index}] jaar {0} is toegestaan")
        @ValueSource(ints = {2000, 2026, 2100})
        @DisplayName("accepteert de grenzen van het bereik")
        void acceptsBoundaries(int year) {
            assertAccepted(() -> new IsoWeek(year, 1));
        }
    }

    @Nested
    @DisplayName("iso_week.range")
    class WeekRange {

        @ParameterizedTest(name = "[{index}] week {0} -> iso_week.range")
        @ValueSource(ints = {0, -1, 54, 100})
        @DisplayName("eist een week tussen 1 en 53")
        void rejectsWeekOutsideRange(int week) {
            assertInvalid("iso_week.range", () -> new IsoWeek(2026, week));
        }
    }

    @Nested
    @DisplayName("iso_week.not_in_year")
    class WeekNotInYear {

        @Test
        @DisplayName("weigert week 53 in een jaar met 52 weken")
        void rejectsWeek53InShortYear() {
            // 2026 heeft 53 ISO-weken; 2025 heeft er 52.
            assertInvalid("iso_week.not_in_year", () -> new IsoWeek(2025, 53));
        }

        @ParameterizedTest(name = "[{index}] {0} heeft 53 weken")
        @ValueSource(ints = {2004, 2009, 2015, 2020, 2026})
        @DisplayName("staat week 53 toe in een jaar met 53 weken")
        void acceptsWeek53InLongYear(int year) {
            assertAccepted(() -> new IsoWeek(year, 53));
        }

        @ParameterizedTest(name = "[{index}] {0} heeft 52 weken")
        @ValueSource(ints = {2021, 2022, 2023, 2024, 2025})
        @DisplayName("weigert week 53 in een jaar met 52 weken")
        void rejectsWeek53InAnyShortYear(int year) {
            assertInvalid("iso_week.not_in_year", () -> new IsoWeek(year, 53));
        }

        @Test
        @DisplayName("de bereikcontrole komt vóór de jaarcontrole")
        void rangeCheckWins() {
            // KARAKTERISERING: week 54 is zowel buiten het bereik als buiten het
            // jaar. iso_week.range wint, omdat die eerst staat.
            assertInvalid("iso_week.range", () -> new IsoWeek(2025, 54));
        }
    }

    @Nested
    @DisplayName("of(LocalDate)")
    class FromDate {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
            // Jaarovergangen: de kern van ISO-8601.
            "2026-01-01, 2026-W01", // donderdag, hoort bij week 1 van 2026
            "2025-01-01, 2025-W01", // woensdag, hoort bij week 1 van 2025
            "2023-01-01, 2022-W52", // zondag, hoort nog bij 2022
            "2022-01-01, 2021-W52", // zaterdag, hoort nog bij 2021
            "2021-01-01, 2020-W53", // vrijdag, hoort bij week 53 van 2020
            "2026-12-31, 2026-W53",
            "2026-02-02, 2026-W06" // maandag uit de seed-data
        })
        @DisplayName("bepaalt het ISO-weekjaar, niet het kalenderjaar")
        void usesWeekBasedYear(LocalDate date, String expected) {
            assertThat(IsoWeek.of(date)).hasToString(expected);
        }

        @Test
        @DisplayName("een datum buiten het toegestane jaarbereik wordt geweigerd")
        void rejectsDateOutsideRange() {
            assertInvalid("iso_year.range", () -> IsoWeek.of(LocalDate.of(1999, 6, 1)));
        }
    }

    @Nested
    @DisplayName("firstDay en lastDay")
    class Boundaries {

        @ParameterizedTest(name = "[{index}] {0}-W{1} loopt van {2} tot {3}")
        @CsvSource({
            "2026, 1,  2025-12-29, 2026-01-04",
            "2026, 6,  2026-02-02, 2026-02-08",
            "2026, 53, 2026-12-28, 2027-01-03",
            "2025, 1,  2024-12-30, 2025-01-05",
            "2020, 53, 2020-12-28, 2021-01-03"
        })
        @DisplayName("begint op maandag en eindigt zes dagen later")
        void spansMondayToSunday(int year, int week, LocalDate expectedFirst, LocalDate expectedLast) {
            IsoWeek isoWeek = new IsoWeek(year, week);

            assertThat(isoWeek.firstDay()).isEqualTo(expectedFirst);
            assertThat(isoWeek.lastDay()).isEqualTo(expectedLast);
        }

        @ParameterizedTest(name = "[{index}] {0}-W{1}")
        @CsvSource({"2026, 1", "2026, 26", "2026, 53", "2000, 1", "2100, 52"})
        @DisplayName("firstDay is altijd een maandag en lastDay altijd een zondag")
        void alwaysMondayAndSunday(int year, int week) {
            IsoWeek isoWeek = new IsoWeek(year, week);

            assertThat(isoWeek.firstDay().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(isoWeek.lastDay().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(isoWeek.firstDay()).isEqualTo(isoWeek.lastDay().minusDays(6));
        }

        @Test
        @DisplayName("een week kan over de jaargrens lopen")
        void mayCrossYearBoundary() {
            assertThat(new IsoWeek(2026, 1).firstDay().getYear()).isEqualTo(2025);
            assertThat(new IsoWeek(2026, 53).lastDay().getYear()).isEqualTo(2027);
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        private final IsoWeek week6 = new IsoWeek(2026, 6);

        @ParameterizedTest(name = "[{index}] {0} in 2026-W06 is {1}")
        @CsvSource({
            "2026-02-01, false", // zondag ervoor
            "2026-02-02, true", // maandag: eerste dag
            "2026-02-05, true",
            "2026-02-08, true", // zondag: laatste dag
            "2026-02-09, false" // maandag erna
        })
        @DisplayName("is inclusief aan beide kanten")
        void inclusiveOnBothEnds(LocalDate date, boolean expected) {
            assertThat(week6.contains(date)).isEqualTo(expected);
        }

        @Test
        @DisplayName("is consistent met of()")
        void consistentWithOf() {
            LocalDate date = LocalDate.of(2026, 2, 5);
            assertThat(IsoWeek.of(date).contains(date)).isTrue();
        }
    }

    @Nested
    @DisplayName("waardegelijkheid")
    class Equality {

        @Test
        @DisplayName("gelijk bij hetzelfde jaar en dezelfde week")
        void equalOnSameYearAndWeek() {
            assertThat(new IsoWeek(2026, 6)).isEqualTo(new IsoWeek(2026, 6)).hasSameHashCodeAs(new IsoWeek(2026, 6));
        }

        @Test
        @DisplayName("ongelijk bij een ander jaar of een andere week")
        void notEqualOtherwise() {
            assertThat(new IsoWeek(2026, 6)).isNotEqualTo(new IsoWeek(2025, 6)).isNotEqualTo(new IsoWeek(2026, 7));
        }

        @Test
        @DisplayName("niet gelijk aan null of een ander type")
        void notEqualToOtherTypes() {
            assertThat(new IsoWeek(2026, 6)).isNotEqualTo(null).isNotEqualTo("2026-W06");
        }
    }

    @Nested
    @DisplayName("toString")
    class StringForm {

        @ParameterizedTest(name = "[{index}] {0}-W{1} -> {2}")
        @CsvSource({"2026, 6, 2026-W06", "2026, 1, 2026-W01", "2026, 53, 2026-W53", "2000, 10, 2000-W10"})
        @DisplayName("vult de week aan tot twee cijfers")
        void padsWeekNumber(int year, int week, String expected) {
            assertThat(new IsoWeek(year, week)).hasToString(expected);
        }
    }
}
