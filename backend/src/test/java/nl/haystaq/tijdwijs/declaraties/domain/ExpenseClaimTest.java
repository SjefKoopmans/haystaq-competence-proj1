package nl.haystaq.tijdwijs.declaraties.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertConflict;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import nl.haystaq.tijdwijs.declaraties.domain.ExpenseClaim.Category;
import nl.haystaq.tijdwijs.declaraties.domain.ExpenseClaim.Status;
import nl.haystaq.tijdwijs.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor het aggregate {@link ExpenseClaim}. Patroon: zie
 * {@code IbanTest}.
 *
 * <p><strong>Alle datums zijn relatief aan vandaag.</strong> {@code
 * expense_date.future} en {@code expense_date.stale} vergelijken met {@code
 * LocalDate.now()} en er is geen {@code Clock} te injecteren.
 */
@DisplayName("ExpenseClaim")
class ExpenseClaimTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID PROJECT_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final LocalDate TODAY = LocalDate.now();
    private static final String RECEIPT = "RCP-000123";

    private static Money euro(String amount) {
        return Money.of(new BigDecimal(amount));
    }

    /** Een geldige declaratie: TRAVEL, €20 (onder de bonnetjesgrens), 21% btw, vandaag. */
    private static ExpenseClaim file() {
        return ExpenseClaim.file(
                EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), "EUR", new BigDecimal("21"), TODAY, null, null);
    }

    private static ExpenseClaim submitted() {
        ExpenseClaim claim = file();
        claim.submit();
        return claim;
    }

    @Nested
    @DisplayName("Category.parse")
    class CategoryParsing {

        @ParameterizedTest
        @NullSource
        @DisplayName("weigert een ontbrekende waarde")
        void rejectsNull(String raw) {
            assertInvalid("category.missing", () -> Category.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> category.unknown")
        @ValueSource(strings = {"", "REIS", "MAALTIJD", "LAPTOP", "  "})
        @DisplayName("weigert een onbekende waarde")
        void rejectsUnknown(String raw) {
            assertInvalid("category.unknown", () -> Category.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(Category.class)
        @DisplayName("herkent elke bestaande waarde")
        void parsesAllValues(Category category) {
            assertThat(Category.parse(category.name())).isEqualTo(category);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({"travel, TRAVEL", "TrAvEl, TRAVEL", "' MEALS ', MEALS", "hardware, HARDWARE"})
        @DisplayName("trimt en negeert kapitalisatie")
        void trimsAndIgnoresCase(String raw, Category expected) {
            assertThat(Category.parse(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("file: verplichte velden")
    class RequiredFields {

        @Test
        @DisplayName("weigert een ontbrekende medewerker")
        void rejectsMissingEmployee() {
            assertInvalid("employee_id.missing", () -> ExpenseClaim.file(
                    null, PROJECT_ID, Category.TRAVEL, euro("20.00"), "EUR", null, TODAY, null, null));
        }

        @Test
        @DisplayName("weigert een ontbrekend bedrag")
        void rejectsMissingAmount() {
            assertInvalid("amount.missing", () -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, null, "EUR", null, TODAY, null, null));
        }

        @Test
        @DisplayName("weigert een ontbrekende bondatum")
        void rejectsMissingDate() {
            assertInvalid("expense_date.missing", () -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), "EUR", null, null, null, null));
        }

        @Test
        @DisplayName("een ontbrekend project is toegestaan")
        void projectIsOptional() {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID, null, Category.TRAVEL, euro("20.00"), "EUR", null, TODAY, null, null));
        }

        @Test
        @DisplayName("een ontbrekende categorie geeft een NullPointerException, geen rule-code")
        void nullCategoryThrowsNpe() {
            // KARAKTERISERING: file() controleert de categorie niet op null, maar
            // roept er wel category.limit() op. Category.parse zou category.missing
            // geven, maar wordt hier niet gebruikt. Zie docs/testing.md.
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> ExpenseClaim.file(
                            EMPLOYEE_ID, PROJECT_ID, null, euro("20.00"), "EUR", null, TODAY, null, null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("file: currency.unknown")
    class Currency {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> currency.unknown")
        @ValueSource(strings = {"", "CHF", "JPY", "EURO", "E U R"})
        @DisplayName("weigert een onbekende valuta")
        void rejectsUnknown(String currency) {
            assertInvalid("currency.unknown", () -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), currency, null, TODAY, null, "Toelichting"));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" is toegestaan")
        @ValueSource(strings = {"EUR", "USD", "GBP", "usd", "gbp"})
        @DisplayName("accepteert EUR, USD en GBP, ongeacht kapitalisatie")
        void acceptsKnownCurrencies(String currency) {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), currency, null, TODAY, null, "Toelichting"));
        }

        @Test
        @DisplayName("een ontbrekende valuta wordt EUR")
        void nullBecomesEuro() {
            ExpenseClaim claim = ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), null, null, TODAY, null, null);

            assertThat(claim.currency()).isEqualTo("EUR");
        }
    }

    @Nested
    @DisplayName("file: vat_rate")
    class VatRate {

        @ParameterizedTest(name = "[{index}] {0}% -> vat_rate.unknown")
        @ValueSource(strings = {"6", "19", "21.5", "-1", "100"})
        @DisplayName("weigert een tarief buiten 0, 9 en 21 procent")
        void rejectsUnknownRate(String rate) {
            assertInvalid("vat_rate.unknown", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("20.00"),
                    "EUR",
                    new BigDecimal(rate),
                    TODAY,
                    null,
                    null));
        }

        @ParameterizedTest(name = "[{index}] {0}% is toegestaan voor TRAVEL")
        @ValueSource(strings = {"0", "9", "21", "0.00", "21.00"})
        @DisplayName("accepteert de toegestane tarieven, ongeacht de schaal")
        void acceptsAllowedRates(String rate) {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("20.00"),
                    "EUR",
                    new BigDecimal(rate),
                    TODAY,
                    null,
                    null));
        }

        @Test
        @DisplayName("een ontbrekend tarief wordt 21 procent")
        void nullBecomes21() {
            ExpenseClaim claim = ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), "EUR", null, TODAY, null, null);

            assertThat(claim.vatRate()).isEqualByComparingTo("21.00");
        }

        @Test
        @DisplayName("het tarief wordt op twee decimalen gezet")
        void normalisesScale() {
            ExpenseClaim claim = ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("20.00"),
                    "EUR",
                    new BigDecimal("9"),
                    TODAY,
                    null,
                    null);

            assertThat(claim.vatRate().scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("file: vat_rate.category")
    class VatRatePerCategory {

        @ParameterizedTest(name = "[{index}] MEALS met {0}% -> vat_rate.category")
        @ValueSource(strings = {"0", "21"})
        @DisplayName("maaltijden moeten 9 procent btw hebben")
        void mealsRequire9Percent(String rate) {
            assertInvalid("vat_rate.category", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.MEALS,
                    euro("20.00"),
                    "EUR",
                    new BigDecimal(rate),
                    TODAY,
                    null,
                    null));
        }

        @ParameterizedTest(name = "[{index}] SOFTWARE met {0}% -> vat_rate.category")
        @ValueSource(strings = {"0", "9"})
        @DisplayName("software moet 21 procent btw hebben")
        void softwareRequires21Percent(String rate) {
            assertInvalid("vat_rate.category", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.SOFTWARE,
                    euro("20.00"),
                    "EUR",
                    new BigDecimal(rate),
                    TODAY,
                    null,
                    null));
        }

        @Test
        @DisplayName("een ontbrekend tarief valt bij maaltijden op de standaard van 21 procent")
        void mealsWithNullRateFails() {
            // KARAKTERISERING: de standaardwaarde 21 wordt eerst toegekend en dan
            // getoetst aan de categorie-eis van 9. Een maaltijddeclaratie zonder
            // expliciet btw-tarief wordt dus altijd geweigerd.
            assertInvalid("vat_rate.category", () -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.MEALS, euro("20.00"), "EUR", null, TODAY, null, null));
        }

        @ParameterizedTest(name = "[{index}] {0} kent geen vast tarief")
        @EnumSource(
                value = Category.class,
                names = {"TRAVEL", "HARDWARE", "OTHER"})
        @DisplayName("de overige categorieën staan elk toegestaan tarief toe")
        void othersAllowAnyRate(Category category) {
            for (String rate : new String[] {"0", "9", "21"}) {
                assertAccepted(() -> ExpenseClaim.file(
                        EMPLOYEE_ID,
                        PROJECT_ID,
                        category,
                        euro("20.00"),
                        "EUR",
                        new BigDecimal(rate),
                        TODAY,
                        null,
                        null));
            }
        }
    }

    @Nested
    @DisplayName("file: amount.category_limit")
    class AmountLimit {

        @ParameterizedTest(name = "[{index}] {0} boven de standaardlimiet")
        @EnumSource(
                value = Category.class,
                names = {"TRAVEL", "OTHER"})
        @DisplayName("weigert meer dan 5000 euro buiten hardware")
        void rejectsAboveStandardLimit(Category category) {
            assertInvalid("amount.category_limit", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    category,
                    euro("5000.01"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    RECEIPT,
                    null));
        }

        @Test
        @DisplayName("staat exact 5000 euro toe")
        void allowsExactlyStandardLimit() {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("5000.00"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    RECEIPT,
                    null));
        }

        @Test
        @DisplayName("hardware mag tot 10000 euro")
        void hardwareHasHigherLimit() {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.HARDWARE,
                    euro("10000.00"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    RECEIPT,
                    null));

            assertInvalid("amount.category_limit", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.HARDWARE,
                    euro("10000.01"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    RECEIPT,
                    null));
        }

        @Test
        @DisplayName("de limietcontrole komt vóór de categorie-btw-eis")
        void limitCheckBeforeVatCategory() {
            assertInvalid("amount.category_limit", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.MEALS,
                    euro("6000.00"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    RECEIPT,
                    null));
        }
    }

    @Nested
    @DisplayName("file: receipt_reference.required")
    class ReceiptReference {

        @ParameterizedTest(name = "[{index}] bon \"{0}\" boven de grens -> receipt_reference.required")
        @CsvSource(
                value = {"NULL", "''", "'RCP-123'", "'rcp-000123'", "'RCP-0001234'", "'123456'"},
                nullValues = "NULL")
        @DisplayName("eist een geldig bonnummer boven 25 euro")
        void requiresReceiptAboveThreshold(String receipt) {
            assertInvalid("receipt_reference.required", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("25.01"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    receipt,
                    null));
        }

        @Test
        @DisplayName("staat exact 25 euro zonder bon toe")
        void allowsExactlyThresholdWithoutReceipt() {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("25.00"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    null,
                    null));
        }

        @Test
        @DisplayName("accepteert een geldig bonnummer boven de grens")
        void acceptsValidReceipt() {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("100.00"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    RECEIPT,
                    null));
        }

        @Test
        @DisplayName("een ongeldig bonnummer onder de grens wordt niet gecontroleerd")
        void receiptNotValidatedBelowThreshold() {
            // KARAKTERISERING: het formaat wordt alleen getoetst boven 25 euro.
            // Onder die grens komt er onzin ongemerkt door.
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("10.00"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY,
                    "onzin",
                    null));
        }
    }

    @Nested
    @DisplayName("file: description.foreign_currency")
    class ForeignCurrency {

        @ParameterizedTest(name = "[{index}] USD met toelichting \"{0}\"")
        @CsvSource(
                value = {"NULL", "''", "'   '"},
                nullValues = "NULL")
        @DisplayName("eist een toelichting bij een andere valuta dan euro")
        void requiresDescriptionForForeignCurrency(String description) {
            assertInvalid("description.foreign_currency", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("20.00"),
                    "USD",
                    new BigDecimal("21"),
                    TODAY,
                    null,
                    description));
        }

        @Test
        @DisplayName("accepteert een vreemde valuta met toelichting")
        void acceptsForeignCurrencyWithDescription() {
            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("20.00"),
                    "USD",
                    new BigDecimal("21"),
                    TODAY,
                    null,
                    "Taxi in New York"));
        }

        @Test
        @DisplayName("een euro-declaratie mag zonder toelichting")
        void euroNeedsNoDescription() {
            assertAccepted(() -> file());
        }
    }

    @Nested
    @DisplayName("file: expense_date")
    class ExpenseDate {

        @ParameterizedTest(name = "[{index}] {0} dagen vooruit -> expense_date.future")
        @ValueSource(longs = {1, 30, 365})
        @DisplayName("weigert een bondatum in de toekomst")
        void rejectsFutureDate(long daysAhead) {
            LocalDate date = TODAY.plusDays(daysAhead);

            assertInvalid("expense_date.future", () -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), "EUR", new BigDecimal("21"), date, null, null));
        }

        @ParameterizedTest(name = "[{index}] {0} dagen terug -> expense_date.stale")
        @ValueSource(longs = {91, 180, 365})
        @DisplayName("weigert een bondatum van meer dan negentig dagen terug")
        void rejectsStaleDate(long daysAgo) {
            LocalDate date = TODAY.minusDays(daysAgo);

            assertInvalid("expense_date.stale", () -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), "EUR", new BigDecimal("21"), date, null, null));
        }

        @ParameterizedTest(name = "[{index}] {0} dagen terug is toegestaan")
        @ValueSource(longs = {0, 1, 45, 90})
        @DisplayName("staat een bondatum tot en met negentig dagen terug toe")
        void acceptsRecentDate(long daysAgo) {
            LocalDate date = TODAY.minusDays(daysAgo);

            assertAccepted(() -> ExpenseClaim.file(
                    EMPLOYEE_ID, PROJECT_ID, Category.TRAVEL, euro("20.00"), "EUR", new BigDecimal("21"), date, null, null));
        }

        @Test
        @DisplayName("de datumcontroles komen als laatste")
        void dateChecksComeLast() {
            // KARAKTERISERING: een declaratie die zowel te oud is als een ongeldig
            // bonnummer heeft, faalt op het bonnummer. De volgorde bepaalt welke
            // code een agent te zien krijgt.
            assertInvalid("receipt_reference.required", () -> ExpenseClaim.file(
                    EMPLOYEE_ID,
                    PROJECT_ID,
                    Category.TRAVEL,
                    euro("100.00"),
                    "EUR",
                    new BigDecimal("21"),
                    TODAY.minusDays(200),
                    null,
                    null));
        }
    }

    @Nested
    @DisplayName("file: resultaat")
    class Result {

        @Test
        @DisplayName("legt een declaratie vast als concept")
        void createsDraft() {
            ExpenseClaim claim = file();

            assertThat(claim.id()).isNotNull();
            assertThat(claim.employeeId()).isEqualTo(EMPLOYEE_ID);
            assertThat(claim.projectId()).isEqualTo(PROJECT_ID);
            assertThat(claim.category()).isEqualTo(Category.TRAVEL);
            assertThat(claim.amount().amount()).isEqualByComparingTo("20.00");
            assertThat(claim.status()).isEqualTo(Status.DRAFT);
        }

        @Test
        @DisplayName("elke declaratie krijgt een nieuw id")
        void generatesUniqueIds() {
            assertThat(file().id()).isNotEqualTo(file().id());
        }
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("dient een concept in")
        void submitsDraft() {
            ExpenseClaim claim = file();

            claim.submit();

            assertThat(claim.status()).isEqualTo(Status.SUBMITTED);
        }

        @Test
        @DisplayName("staat opnieuw indienen na afkeuring toe")
        void allowsResubmissionAfterRejection() {
            ExpenseClaim claim = submitted();
            claim.decide(false);

            assertAccepted(claim::submit);
            assertThat(claim.status()).isEqualTo(Status.SUBMITTED);
        }

        @ParameterizedTest(name = "[{index}] indienen vanuit {0}")
        @EnumSource(
                value = Status.class,
                names = {"SUBMITTED", "APPROVED", "PAID"})
        @DisplayName("weigert indienen vanuit de overige statussen")
        void rejectsFromOtherStatuses(Status status) {
            ExpenseClaim claim = claimIn(status);

            assertConflict("status.not_submittable", claim::submit);
        }
    }

    @Nested
    @DisplayName("decide")
    class Decide {

        @Test
        @DisplayName("keurt een ingediende declaratie goed")
        void approves() {
            ExpenseClaim claim = submitted();

            claim.decide(true);

            assertThat(claim.status()).isEqualTo(Status.APPROVED);
        }

        @Test
        @DisplayName("keurt een ingediende declaratie af")
        void rejects() {
            ExpenseClaim claim = submitted();

            claim.decide(false);

            assertThat(claim.status()).isEqualTo(Status.REJECTED);
        }

        @ParameterizedTest(name = "[{index}] beslissen vanuit {0}")
        @EnumSource(
                value = Status.class,
                names = {"DRAFT", "APPROVED", "REJECTED", "PAID"})
        @DisplayName("weigert beslissen vanuit elke andere status")
        void rejectsFromOtherStatuses(Status status) {
            ExpenseClaim claim = claimIn(status);

            assertConflict("status.not_decidable", () -> claim.decide(true));
        }

        @Test
        @DisplayName("er is geen bevoegdheidscontrole op de beslisser")
        void noApproverCheck() {
            // KARAKTERISERING: decide() kent geen goedkeurder en geen
            // bevoegdheidscontrole. Vergelijk Timesheet.approve, dat approver.self
            // en approver.not_authorised bewaakt. De controle zit in
            // ExpenseClaimService.
            assertAccepted(() -> submitted().decide(true));
        }
    }

    @Nested
    @DisplayName("markPaid")
    class MarkPaid {

        @Test
        @DisplayName("markeert een goedgekeurde declaratie als betaald")
        void marksApprovedAsPaid() {
            ExpenseClaim claim = submitted();
            claim.decide(true);

            claim.markPaid();

            assertThat(claim.status()).isEqualTo(Status.PAID);
        }

        @ParameterizedTest(name = "[{index}] betalen vanuit {0}")
        @EnumSource(
                value = Status.class,
                names = {"DRAFT", "SUBMITTED", "REJECTED", "PAID"})
        @DisplayName("weigert betalen vanuit elke andere status")
        void rejectsFromOtherStatuses(Status status) {
            ExpenseClaim claim = claimIn(status);

            assertConflict("status.not_payable", claim::markPaid);
        }

        @Test
        @DisplayName("PAID is een eindtoestand")
        void paidIsTerminal() {
            ExpenseClaim claim = claimIn(Status.PAID);

            assertConflict("status.not_submittable", claim::submit);
            assertConflict("status.not_decidable", () -> claim.decide(true));
            assertConflict("status.not_payable", claim::markPaid);
        }
    }

    /** Brengt een declaratie in de gevraagde status, voor de statusmatrixtests. */
    private static ExpenseClaim claimIn(Status status) {
        ExpenseClaim claim = file();
        switch (status) {
            case DRAFT -> {
                /* al DRAFT */
            }
            case SUBMITTED -> claim.submit();
            case APPROVED -> {
                claim.submit();
                claim.decide(true);
            }
            case REJECTED -> {
                claim.submit();
                claim.decide(false);
            }
            case PAID -> {
                claim.submit();
                claim.decide(true);
                claim.markPaid();
            }
        }
        return claim;
    }
}
