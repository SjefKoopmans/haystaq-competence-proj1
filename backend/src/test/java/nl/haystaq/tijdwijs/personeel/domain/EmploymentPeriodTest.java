package nl.haystaq.tijdwijs.personeel.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor {@link EmploymentPeriod}. Patroon: zie {@code IbanTest}.
 *
 * <p><strong>Alle datums zijn relatief aan vandaag.</strong> {@code
 * hire_date.future} vergelijkt met {@code LocalDate.now().plusDays(365)} en er
 * is geen {@code Clock} te injecteren; datumliterals zouden hier op een
 * willekeurige dag gaan falen.
 */
@DisplayName("EmploymentPeriod")
class EmploymentPeriodTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Nested
    @DisplayName("hire_date.missing")
    class Missing {

        @Test
        @DisplayName("weigert een ontbrekende indienstdatum")
        void rejectsNullHireDate() {
            assertInvalid("hire_date.missing", () -> new EmploymentPeriod(null, null));
        }

        @Test
        @DisplayName("weigert null ook als er een einddatum is")
        void rejectsNullHireDateWithEndDate() {
            assertInvalid("hire_date.missing", () -> new EmploymentPeriod(null, TODAY));
        }
    }

    @Nested
    @DisplayName("hire_date.range")
    class Range {

        @ParameterizedTest(name = "[{index}] {0} -> hire_date.range")
        @ValueSource(strings = {"1989-12-31", "1980-01-01", "1900-01-01"})
        @DisplayName("weigert een indienstdatum vóór 1990-01-01")
        void rejectsBefore1990(LocalDate hireDate) {
            assertInvalid("hire_date.range", () -> new EmploymentPeriod(hireDate, null));
        }

        @Test
        @DisplayName("accepteert exact 1990-01-01")
        void acceptsEarliestAllowed() {
            assertAccepted(() -> new EmploymentPeriod(LocalDate.of(1990, 1, 1), null));
        }
    }

    @Nested
    @DisplayName("hire_date.future")
    class Future {

        @ParameterizedTest(name = "[{index}] vandaag + {0} dagen -> hire_date.future")
        @ValueSource(longs = {366, 400, 3650})
        @DisplayName("weigert een indienstdatum meer dan 365 dagen vooruit")
        void rejectsTooFarInFuture(long days) {
            LocalDate hireDate = TODAY.plusDays(days);
            assertInvalid("hire_date.future", () -> new EmploymentPeriod(hireDate, null));
        }

        @ParameterizedTest(name = "[{index}] vandaag + {0} dagen is toegestaan")
        @ValueSource(longs = {0, 1, 180, 365})
        @DisplayName("staat een indienstdatum tot en met 365 dagen vooruit toe")
        void acceptsUpTo365Days(long days) {
            // KARAKTERISERING: een dienstverband mag een jaar vooruit worden
            // vastgelegd. Staat niet in docs/business-rules.md.
            LocalDate hireDate = TODAY.plusDays(days);
            assertAccepted(() -> new EmploymentPeriod(hireDate, null));
        }

        @Test
        @DisplayName("de bereikcontrole komt vóór de toekomstcontrole")
        void rangeCheckComesFirst() {
            assertInvalid("hire_date.range", () -> new EmploymentPeriod(LocalDate.of(1985, 1, 1), null));
        }
    }

    @Nested
    @DisplayName("end_date.order")
    class Order {

        @ParameterizedTest(name = "[{index}] einddatum {0} dagen na indienst -> end_date.order")
        @ValueSource(longs = {0, -1, -365})
        @DisplayName("eist dat de einddatum strikt ná de indienstdatum ligt")
        void rejectsEndDateNotAfterHireDate(long offsetDays) {
            LocalDate hireDate = TODAY.minusYears(1);
            LocalDate endDate = hireDate.plusDays(offsetDays);
            assertInvalid("end_date.order", () -> new EmploymentPeriod(hireDate, endDate));
        }

        @Test
        @DisplayName("een einddatum één dag later is toegestaan")
        void acceptsOneDayLater() {
            LocalDate hireDate = TODAY.minusYears(1);
            assertAccepted(() -> new EmploymentPeriod(hireDate, hireDate.plusDays(1)));
        }

        @Test
        @DisplayName("een einddatum in de toekomst is toegestaan")
        void acceptsFutureEndDate() {
            // KARAKTERISERING: de einddatum kent geen bovengrens, alleen de
            // ordening ten opzichte van de indienstdatum wordt bewaakt.
            assertAccepted(() -> new EmploymentPeriod(TODAY.minusYears(1), TODAY.plusYears(50)));
        }
    }

    @Nested
    @DisplayName("geldige invoer")
    class Accepted {

        @Test
        @DisplayName("een lopend dienstverband zonder einddatum")
        void openEnded() {
            EmploymentPeriod period = new EmploymentPeriod(TODAY.minusYears(3), null);

            assertThat(period.hireDate()).isEqualTo(TODAY.minusYears(3));
            assertThat(period.endDate()).isNull();
        }

        @Test
        @DisplayName("een afgesloten dienstverband")
        void closed() {
            LocalDate hireDate = TODAY.minusYears(3);
            LocalDate endDate = TODAY.minusYears(1);
            EmploymentPeriod period = new EmploymentPeriod(hireDate, endDate);

            assertThat(period.hireDate()).isEqualTo(hireDate);
            assertThat(period.endDate()).isEqualTo(endDate);
        }
    }

    @Nested
    @DisplayName("covers")
    class Covers {

        @ParameterizedTest(name = "[{index}] dag {0} van een lopend dienstverband -> {1}")
        @CsvSource({"-1, false", "0, true", "1, true", "500, true"})
        @DisplayName("dekt alles vanaf de indienstdatum als er geen einddatum is")
        void openEndedCoversFromHireDate(long offsetFromHire, boolean expected) {
            LocalDate hireDate = TODAY.minusYears(1);
            EmploymentPeriod period = new EmploymentPeriod(hireDate, null);

            assertThat(period.covers(hireDate.plusDays(offsetFromHire))).isEqualTo(expected);
        }

        @Test
        @DisplayName("is inclusief aan beide kanten")
        void inclusiveOnBothEnds() {
            LocalDate hireDate = TODAY.minusYears(3);
            LocalDate endDate = TODAY.minusYears(1);
            EmploymentPeriod period = new EmploymentPeriod(hireDate, endDate);

            assertThat(period.covers(hireDate)).isTrue();
            assertThat(period.covers(endDate)).isTrue();
            assertThat(period.covers(hireDate.minusDays(1))).isFalse();
            assertThat(period.covers(endDate.plusDays(1))).isFalse();
        }
    }
}
