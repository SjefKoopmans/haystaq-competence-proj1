package nl.haystaq.tijdwijs.personeel.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertConflict;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import nl.haystaq.tijdwijs.shared.domain.Hours;
import nl.haystaq.tijdwijs.shared.domain.Money;
import nl.haystaq.tijdwijs.testsupport.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor het aggregate {@link Employee}. Patroon: zie
 * {@code IbanTest}; geldige uitgangswaarden komen uit {@link Fixtures}.
 */
@DisplayName("Employee")
class EmployeeTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("legt een geldige medewerker vast met een eigen id")
        void registersValidEmployee() {
            Employee employee = Fixtures.employee();

            assertThat(employee.id()).isNotNull();
            assertThat(employee.employeeCode().value()).isEqualTo(Fixtures.VALID_EMPLOYEE_CODE);
            assertThat(employee.contractType()).isEqualTo(ContractType.PERMANENT);
            assertThat(employee.isActive()).isTrue();
            assertThat(employee.managerId()).isNull();
        }

        @Test
        @DisplayName("elke registratie krijgt een nieuw id")
        void generatesUniqueIds() {
            assertThat(Fixtures.employee().id()).isNotEqualTo(Fixtures.employee().id());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("weigert een ontbrekende voornaam; de rule-code is de veldnaam")
        void rejectsMissingFirstName(String value) {
            // KARAKTERISERING: de code is letterlijk "first_name", zonder achtervoegsel
            // zoals ".missing" of ".format". Afwijkend van de rest van het domein.
            assertInvalid("first_name", () -> Fixtures.employeeBuilder().firstName(value).build());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("weigert een ontbrekende achternaam")
        void rejectsMissingLastName(String value) {
            assertInvalid("last_name", () -> Fixtures.employeeBuilder().lastName(value).build());
        }

        @Test
        @DisplayName("weigert een naam van meer dan 60 tekens")
        void rejectsTooLongName() {
            String tooLong = "a".repeat(61);
            assertInvalid("first_name", () -> Fixtures.employeeBuilder().firstName(tooLong).build());
            assertInvalid("last_name", () -> Fixtures.employeeBuilder().lastName(tooLong).build());
        }

        @Test
        @DisplayName("accepteert een naam van exact 60 tekens en trimt de waarde")
        void acceptsBoundaryNameAndTrims() {
            Employee employee =
                    Fixtures.employeeBuilder().firstName("  Sanne  ").lastName("a".repeat(60)).build();

            assertThat(employee.firstName()).isEqualTo("Sanne");
            assertThat(employee.lastName()).hasSize(60);
        }

        @Test
        @DisplayName("de lengte wordt na trimmen gemeten")
        void lengthMeasuredAfterTrim() {
            // KARAKTERISERING: 60 tekens met omliggende ruimte is geldig, want de
            // controle gebruikt trim().length().
            assertAccepted(() -> Fixtures.employeeBuilder().firstName("  " + "a".repeat(60) + "  ").build());
        }

        @Test
        @DisplayName("weigert een ontbrekende geboortedatum")
        void rejectsMissingBirthDate() {
            assertInvalid("birth_date.missing", () -> Fixtures.employeeBuilder().birthDate(null).build());
        }
    }

    @Nested
    @DisplayName("leeftijdsgrenzen")
    class Age {

        @Test
        @DisplayName("weigert een medewerker die bij indiensttreding jonger was dan 16")
        void rejectsTooYoungAtHire() {
            LocalDate hireDate = TODAY.minusYears(1);
            LocalDate birthDate = hireDate.minusYears(16).plusDays(1); // net geen 16
            assertInvalid("age.minimum", () -> Fixtures.employeeBuilder()
                    .period(hireDate, null)
                    .birthDate(birthDate)
                    .build());
        }

        @Test
        @DisplayName("accepteert exact 16 jaar bij indiensttreding")
        void acceptsExactly16AtHire() {
            LocalDate hireDate = TODAY.minusYears(1);
            assertAccepted(() -> Fixtures.employeeBuilder()
                    .period(hireDate, null)
                    .birthDate(hireDate.minusYears(16))
                    .build());
        }

        @Test
        @DisplayName("de leeftijdsgrens wordt gemeten op de indienstdatum, niet vandaag")
        void minimumMeasuredAtHireDate() {
            // KARAKTERISERING: age.minimum gebruikt period.hireDate(), age.maximum
            // gebruikt LocalDate.now(). Twee verschillende peildata in één regelset.
            LocalDate hireDate = TODAY.minusYears(10);
            assertInvalid("age.minimum", () -> Fixtures.employeeBuilder()
                    .period(hireDate, null)
                    .birthDate(hireDate.minusYears(15))
                    .build());
        }

        @Test
        @DisplayName("weigert een medewerker die nu ouder is dan 70")
        void rejectsTooOldNow() {
            assertInvalid("age.maximum", () -> Fixtures.employeeBuilder()
                    .birthDate(TODAY.minusYears(71))
                    .build());
        }

        @Test
        @DisplayName("accepteert exact 70 jaar")
        void acceptsExactly70() {
            assertAccepted(() -> Fixtures.employeeBuilder().birthDate(TODAY.minusYears(70)).build());
        }
    }

    @Nested
    @DisplayName("contract_hours")
    class ContractHours {

        @Test
        @DisplayName("weigert ontbrekende contracturen")
        void rejectsMissing() {
            assertInvalid("contract_hours.missing", () -> Fixtures.employeeBuilder()
                    .contractHours((Hours) null)
                    .build());
        }

        @ParameterizedTest(name = "[{index}] {0} uur -> contract_hours.max")
        @ValueSource(strings = {"40.25", "41.00", "60.00"})
        @DisplayName("weigert meer dan 40 uur")
        void rejectsAboveMaximum(String hours) {
            assertInvalid("contract_hours.max", () -> Fixtures.employeeBuilder()
                    .contractHours(hours)
                    .build());
        }

        @ParameterizedTest(name = "[{index}] {0} uur -> contract_hours.step")
        @ValueSource(strings = {"0.25", "7.75", "32.25", "39.75"})
        @DisplayName("eist stappen van een half uur, strenger dan Hours zelf")
        void rejectsQuarterSteps(String hours) {
            // KARAKTERISERING: Hours staat kwartieren toe, maar contracturen moeten
            // in halve uren. Twee verschillende stapgroottes in één domein.
            assertInvalid("contract_hours.step", () -> Fixtures.employeeBuilder()
                    .contractHours(hours)
                    .build());
        }

        @ParameterizedTest(name = "[{index}] {0} uur is toegestaan")
        @ValueSource(strings = {"0.50", "8.00", "32.00", "36.50", "40.00"})
        @DisplayName("accepteert halve uren tot en met 40")
        void acceptsHalfHours(String hours) {
            assertAccepted(() -> Fixtures.employeeBuilder().contractHours(hours).build());
        }

        @Test
        @DisplayName("de maximumcontrole komt vóór de stapcontrole")
        void maxCheckComesFirst() {
            assertInvalid("contract_hours.max", () -> Fixtures.employeeBuilder()
                    .contractHours("40.25")
                    .build());
        }
    }

    @Nested
    @DisplayName("phone.format")
    class Phone {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> phone.format")
        @ValueSource(
                strings = {
                    "abc",
                    "+31", // te kort
                    "0612345", // zeven cijfers
                    "06-12345678", // koppelteken niet toegestaan
                    "(06) 12345678",
                    "+31 6 1234 5678 9012 3456" // te lang
                })
        @DisplayName("weigert een onjuist telefoonnummer")
        void rejectsMalformed(String phone) {
            assertInvalid("phone.format", () -> Fixtures.employeeBuilder().phone(phone).build());
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" is toegestaan")
        @ValueSource(strings = {"+31 6 12345678", "0612345678", "+31612345678", "06 12 34 56 78"})
        @DisplayName("accepteert gangbare notaties")
        void acceptsValid(String phone) {
            assertAccepted(() -> Fixtures.employeeBuilder().phone(phone).build());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("een leeg telefoonnummer is toegestaan")
        void blankIsOptional(String phone) {
            // KARAKTERISERING: het veld is optioneel. null en blank glippen door de
            // formaatcontrole heen.
            assertAccepted(() -> Fixtures.employeeBuilder().phone(phone).build());
        }
    }

    @Nested
    @DisplayName("tarief per contractvorm")
    class Rate {

        @Test
        @DisplayName("weigert een stagiairtarief boven 45,00")
        void rejectsInternRateTooHigh() {
            assertInvalid("intern.rate", () -> Fixtures.employeeBuilder()
                    .contractType(ContractType.INTERN)
                    .hourlyRate("45.01")
                    .build());
        }

        @Test
        @DisplayName("weigert een freelancetarief onder 60,00")
        void rejectsFreelanceRateTooLow() {
            assertInvalid("freelance.rate", () -> Fixtures.employeeBuilder()
                    .contractType(ContractType.FREELANCE)
                    .hourlyRate("59.99")
                    .build());
        }

        @Test
        @DisplayName("accepteert de grenswaarden")
        void acceptsBoundaries() {
            assertAccepted(() -> Fixtures.employeeBuilder()
                    .contractType(ContractType.INTERN)
                    .hourlyRate("45.00")
                    .build());
            assertAccepted(() -> Fixtures.employeeBuilder()
                    .contractType(ContractType.FREELANCE)
                    .hourlyRate("60.00")
                    .build());
        }
    }

    @Nested
    @DisplayName("changeContract")
    class ChangeContract {

        @Test
        @DisplayName("wijzigt contractvorm, uren en tarief")
        void changesContract() {
            Employee employee = Fixtures.employee();

            employee.changeContract(ContractType.TEMPORARY, Hours.of("32.00"), Money.of(new BigDecimal("78.50")));

            assertThat(employee.contractType()).isEqualTo(ContractType.TEMPORARY);
            assertThat(employee.contractHours()).isEqualTo(Hours.of("32.00"));
            assertThat(employee.hourlyRate().amount()).isEqualByComparingTo("78.50");
        }

        @Test
        @DisplayName("bewaakt dezelfde regels als bij registratie")
        void enforcesSameRules() {
            Employee employee = Fixtures.employee();

            assertInvalid("contract_hours.missing", () -> employee.changeContract(ContractType.PERMANENT, null, Money.of(new BigDecimal("95.00"))));
            assertInvalid("contract_hours.max", () -> employee.changeContract(ContractType.PERMANENT, Hours.of("41.00"), Money.of(new BigDecimal("95.00"))));
            assertInvalid("contract_hours.step", () -> employee.changeContract(ContractType.PERMANENT, Hours.of("39.75"), Money.of(new BigDecimal("95.00"))));
            assertInvalid("intern.rate", () -> employee.changeContract(ContractType.INTERN, Hours.of("40.00"), Money.of(new BigDecimal("95.00"))));
            assertInvalid("freelance.rate", () -> employee.changeContract(ContractType.FREELANCE, Hours.of("40.00"), Money.of(new BigDecimal("50.00"))));
        }

        @Test
        @DisplayName("laat de medewerker onaangeroerd als een regel faalt")
        void leavesStateUntouchedOnFailure() {
            Employee employee = Fixtures.employee();

            assertInvalid("contract_hours.max", () -> employee.changeContract(ContractType.TEMPORARY, Hours.of("41.00"), Money.of(new BigDecimal("78.50"))));

            assertThat(employee.contractType()).isEqualTo(ContractType.PERMANENT);
            assertThat(employee.contractHours()).isEqualTo(Hours.of("40.00"));
        }
    }

    @Nested
    @DisplayName("changeContactDetails")
    class ChangeContactDetails {

        @Test
        @DisplayName("wijzigt e-mail, telefoon en IBAN")
        void changesDetails() {
            Employee employee = Fixtures.employee();

            employee.changeContactDetails(
                    new EmailAddress("nieuw@haystaq.nl"), "0612345678", new Iban(Fixtures.VALID_IBAN_ALT));

            assertThat(employee.email().value()).isEqualTo("nieuw@haystaq.nl");
            assertThat(employee.phone()).isEqualTo("0612345678");
            assertThat(employee.iban().value()).isEqualTo(Fixtures.VALID_IBAN_ALT);
        }

        @Test
        @DisplayName("weigert een onjuist telefoonnummer")
        void rejectsBadPhone() {
            Employee employee = Fixtures.employee();

            assertInvalid("phone.format", () -> employee.changeContactDetails(employee.email(), "abc", employee.iban()));
        }

        @Test
        @DisplayName("staat een lege e-mail of IBAN toe")
        void allowsNullEmailAndIban() {
            // KARAKTERISERING: alleen het telefoonnummer wordt gecontroleerd. null
            // voor e-mail of IBAN glipt hier door, terwijl register() dat via de
            // value objects zou tegenhouden.
            Employee employee = Fixtures.employee();

            assertAccepted(() -> employee.changeContactDetails(null, null, null));
            assertThat(employee.email()).isNull();
            assertThat(employee.iban()).isNull();
        }
    }

    @Nested
    @DisplayName("assignManager")
    class AssignManager {

        @Test
        @DisplayName("wijst een leidinggevende toe")
        void assignsManager() {
            Employee employee = Fixtures.employee();
            UUID managerId = UUID.randomUUID();

            employee.assignManager(managerId);

            assertThat(employee.managerId()).isEqualTo(managerId);
            assertThat(employee.isManagedBy(managerId)).isTrue();
        }

        @Test
        @DisplayName("weigert de medewerker als eigen leidinggevende")
        void rejectsSelfAsManager() {
            Employee employee = Fixtures.employee();

            assertConflict("manager.self", () -> employee.assignManager(employee.id()));
        }

        @Test
        @DisplayName("null verwijdert de leidinggevende")
        void nullClearsManager() {
            Employee employee = Fixtures.employee();
            employee.assignManager(UUID.randomUUID());

            employee.assignManager(null);

            assertThat(employee.managerId()).isNull();
        }

        @Test
        @DisplayName("isManagedBy is onwaar zonder leidinggevende")
        void isManagedByFalseWithoutManager() {
            assertThat(Fixtures.employee().isManagedBy(UUID.randomUUID())).isFalse();
            assertThat(Fixtures.employee().isManagedBy(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("endEmployment")
    class EndEmployment {

        @Test
        @DisplayName("een einddatum in het verleden deactiveert de medewerker")
        void pastEndDateDeactivates() {
            Employee employee = Fixtures.employee();

            employee.endEmployment(TODAY.minusDays(1));

            assertThat(employee.employmentPeriod().endDate()).isEqualTo(TODAY.minusDays(1));
            assertThat(employee.isActive()).isFalse();
        }

        @Test
        @DisplayName("een einddatum vandaag deactiveert de medewerker")
        void todayDeactivates() {
            Employee employee = Fixtures.employee();

            employee.endEmployment(TODAY);

            assertThat(employee.isActive()).isFalse();
        }

        @Test
        @DisplayName("een einddatum in de toekomst laat de medewerker actief")
        void futureEndDateKeepsActive() {
            Employee employee = Fixtures.employee();

            employee.endEmployment(TODAY.plusDays(1));

            assertThat(employee.employmentPeriod().endDate()).isEqualTo(TODAY.plusDays(1));
            assertThat(employee.isActive()).isTrue();
        }

        @Test
        @DisplayName("null maakt het dienstverband weer open en laat active ongemoeid")
        void nullReopensPeriod() {
            // KARAKTERISERING: endEmployment(null) wist de einddatum, maar zet
            // active niet terug op true. Daarvoor is reactivate() nodig.
            Employee employee = Fixtures.employee();
            employee.endEmployment(TODAY.minusDays(1));

            employee.endEmployment(null);

            assertThat(employee.employmentPeriod().endDate()).isNull();
            assertThat(employee.isActive()).isFalse();
        }

        @Test
        @DisplayName("weigert een einddatum op of vóór de indienstdatum")
        void rejectsEndDateBeforeHireDate() {
            Employee employee = Fixtures.employee();
            LocalDate hireDate = employee.employmentPeriod().hireDate();

            assertInvalid("end_date.order", () -> employee.endEmployment(hireDate));
            assertInvalid("end_date.order", () -> employee.endEmployment(hireDate.minusDays(1)));
        }
    }

    @Nested
    @DisplayName("reactivate en deactivate")
    class Activation {

        @Test
        @DisplayName("activeert een medewerker zonder einddatum")
        void reactivatesOpenEmployment() {
            Employee employee = Fixtures.employeeBuilder().active(false).build();

            employee.reactivate();

            assertThat(employee.isActive()).isTrue();
        }

        @Test
        @DisplayName("weigert activeren als het dienstverband is afgelopen")
        void rejectsReactivationAfterEnd() {
            Employee employee = Fixtures.employee();
            employee.endEmployment(TODAY.minusDays(1));

            assertConflict("employee.employment_ended", employee::reactivate);
        }

        @Test
        @DisplayName("staat activeren toe bij een einddatum in de toekomst")
        void allowsReactivationBeforeEnd() {
            Employee employee = Fixtures.employee();
            employee.endEmployment(TODAY.plusDays(30));
            employee.deactivate();

            assertAccepted(employee::reactivate);
            assertThat(employee.isActive()).isTrue();
        }

        @Test
        @DisplayName("deactiveren kan altijd, zonder controle")
        void deactivateAlwaysAllowed() {
            Employee employee = Fixtures.employee();

            employee.deactivate();
            assertThat(employee.isActive()).isFalse();

            assertAccepted(employee::deactivate);
        }
    }

    @Nested
    @DisplayName("assertCanBookOn")
    class CanBookOn {

        @Test
        @DisplayName("staat boeken toe binnen een lopend dienstverband")
        void allowsBookingWithinEmployment() {
            Employee employee = Fixtures.employee();

            assertAccepted(() -> employee.assertCanBookOn(TODAY));
            assertAccepted(() -> employee.assertCanBookOn(employee.employmentPeriod().hireDate()));
        }

        @Test
        @DisplayName("weigert boeken door een inactieve medewerker")
        void rejectsInactiveEmployee() {
            Employee employee = Fixtures.employeeBuilder().active(false).build();

            assertConflict("employee.inactive", () -> employee.assertCanBookOn(TODAY));
        }

        @Test
        @DisplayName("weigert boeken vóór de indienstdatum")
        void rejectsBeforeHireDate() {
            Employee employee = Fixtures.employee();
            LocalDate beforeHire = employee.employmentPeriod().hireDate().minusDays(1);

            assertConflict("work_date.before_hire", () -> employee.assertCanBookOn(beforeHire));
        }

        @Test
        @DisplayName("weigert boeken na de einddatum")
        void rejectsAfterEmployment() {
            Employee employee = Fixtures.employee();
            employee.endEmployment(TODAY.plusDays(10));

            assertConflict("work_date.after_employment", () -> employee.assertCanBookOn(TODAY.plusDays(11)));
        }

        @Test
        @DisplayName("staat boeken op de einddatum zelf toe")
        void allowsBookingOnEndDate() {
            Employee employee = Fixtures.employee();
            employee.endEmployment(TODAY.plusDays(10));

            assertAccepted(() -> employee.assertCanBookOn(TODAY.plusDays(10)));
        }

        @Test
        @DisplayName("de actief-controle komt vóór de datumcontroles")
        void inactiveCheckComesFirst() {
            // Een inactieve medewerker met een datum vóór indiensttreding overtreedt
            // twee regels; employee.inactive wint omdat die eerst staat.
            Employee employee = Fixtures.employeeBuilder().active(false).build();
            LocalDate beforeHire = employee.employmentPeriod().hireDate().minusDays(1);

            assertConflict("employee.inactive", () -> employee.assertCanBookOn(beforeHire));
        }
    }
}
