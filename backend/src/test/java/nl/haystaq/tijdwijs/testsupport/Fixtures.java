package nl.haystaq.tijdwijs.testsupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import nl.haystaq.tijdwijs.personeel.domain.ContractType;
import nl.haystaq.tijdwijs.personeel.domain.EmailAddress;
import nl.haystaq.tijdwijs.personeel.domain.Employee;
import nl.haystaq.tijdwijs.personeel.domain.EmployeeCode;
import nl.haystaq.tijdwijs.personeel.domain.EmploymentPeriod;
import nl.haystaq.tijdwijs.personeel.domain.Iban;
import nl.haystaq.tijdwijs.shared.domain.Hours;
import nl.haystaq.tijdwijs.shared.domain.Money;

/**
 * Geldige uitgangswaarden voor tests, volgens het "one bad field"-patroon: elke
 * builder levert per default een object op dat alle invarianten haalt, zodat een
 * test precies één veld ongeldig maakt. Faalt de test dan, staat vast dat het
 * door dát veld komt.
 *
 * <p><strong>Alle datums zijn relatief aan {@link LocalDate#now()}.</strong> Het
 * domein bevat drie regels die de systeemklok gebruiken zonder dat er een
 * {@code Clock} te injecteren is:
 *
 * <ul>
 *   <li>{@code EmploymentPeriod} — {@code hire_date.future} (nu + 365 dagen)
 *   <li>{@code Absence} — {@code sick.retroactive} (14 dagen terug)
 *   <li>{@code ExpenseClaim} — {@code expense_date.future} en {@code
 *       expense_date.stale} (90 dagen)
 * </ul>
 *
 * Datumliterals zouden daardoor op een willekeurige dag spontaan gaan falen.
 * Gebruik dus altijd deze constanten of {@code LocalDate.now().plus/minusDays}.
 */
public final class Fixtures {

    /** Geldige NL-IBAN (18 tekens, mod-97 correct). */
    public static final String VALID_IBAN = "NL91ABNA0417164300";

    /** Tweede geldige NL-IBAN, voor tests die twee verschillende nodig hebben. */
    public static final String VALID_IBAN_ALT = "NL44RABO0123456789";

    public static final String VALID_EMPLOYEE_CODE = "EMP-0001";
    public static final String VALID_PROJECT_CODE = "PRJ-2026-001";
    public static final String VALID_RECEIPT_REFERENCE = "RCP-000123";

    /** Ruim binnen alle datumgrenzen: dienstverband loopt, geen toekomst. */
    public static final LocalDate TODAY = LocalDate.now();

    public static final LocalDate VALID_HIRE_DATE = TODAY.minusYears(3);

    /** Leeftijd 35: boven {@code age.minimum} (16) en onder {@code age.maximum} (70). */
    public static final LocalDate VALID_BIRTH_DATE = TODAY.minusYears(35);

    private Fixtures() {}

    /** Geldig dienstverband zonder einddatum. */
    public static EmploymentPeriod employmentPeriod() {
        return new EmploymentPeriod(VALID_HIRE_DATE, null);
    }

    /**
     * Medewerker die alle invarianten haalt: PERMANENT, 40 uur, €95,00, actief.
     *
     * <p>Let op: {@code hourlyRate} moet passen bij {@code contractType} —
     * INTERN mag maximaal €45,00, FREELANCE minimaal €60,00.
     */
    public static Employee employee() {
        return employeeBuilder().build();
    }

    public static EmployeeBuilder employeeBuilder() {
        return new EmployeeBuilder();
    }

    /**
     * Builder met uitsluitend geldige defaults. Overschrijf precies één veld om
     * één regel te testen.
     */
    public static final class EmployeeBuilder {

        private EmployeeCode code = new EmployeeCode(VALID_EMPLOYEE_CODE);
        private String firstName = "Sanne";
        private String lastName = "de Wit";
        private EmailAddress email = new EmailAddress("sanne.de.wit@haystaq.nl");
        private LocalDate birthDate = VALID_BIRTH_DATE;
        private EmploymentPeriod period = employmentPeriod();
        private ContractType contractType = ContractType.PERMANENT;
        private Hours contractHours = Hours.of("40.00");
        private Money hourlyRate = Money.of(new BigDecimal("95.00"));
        private Iban iban = new Iban(VALID_IBAN);
        private String phone = "+31 6 12345678";
        private java.util.UUID managerId = null;
        private boolean active = true;

        private EmployeeBuilder() {}

        public EmployeeBuilder code(String value) {
            this.code = new EmployeeCode(value);
            return this;
        }

        public EmployeeBuilder firstName(String value) {
            this.firstName = value;
            return this;
        }

        public EmployeeBuilder lastName(String value) {
            this.lastName = value;
            return this;
        }

        public EmployeeBuilder email(String value) {
            this.email = new EmailAddress(value);
            return this;
        }

        public EmployeeBuilder birthDate(LocalDate value) {
            this.birthDate = value;
            return this;
        }

        public EmployeeBuilder period(EmploymentPeriod value) {
            this.period = value;
            return this;
        }

        public EmployeeBuilder period(LocalDate hireDate, LocalDate endDate) {
            this.period = new EmploymentPeriod(hireDate, endDate);
            return this;
        }

        public EmployeeBuilder contractType(ContractType value) {
            this.contractType = value;
            return this;
        }

        public EmployeeBuilder contractHours(String value) {
            this.contractHours = Hours.of(value);
            return this;
        }

        public EmployeeBuilder contractHours(Hours value) {
            this.contractHours = value;
            return this;
        }

        public EmployeeBuilder hourlyRate(String value) {
            this.hourlyRate = Money.of(new BigDecimal(value));
            return this;
        }

        public EmployeeBuilder hourlyRate(Money value) {
            this.hourlyRate = value;
            return this;
        }

        public EmployeeBuilder iban(String value) {
            this.iban = new Iban(value);
            return this;
        }

        public EmployeeBuilder phone(String value) {
            this.phone = value;
            return this;
        }

        public EmployeeBuilder managerId(java.util.UUID value) {
            this.managerId = value;
            return this;
        }

        public EmployeeBuilder active(boolean value) {
            this.active = value;
            return this;
        }

        public Employee build() {
            return Employee.register(
                    code,
                    firstName,
                    lastName,
                    email,
                    birthDate,
                    period,
                    contractType,
                    contractHours,
                    hourlyRate,
                    iban,
                    phone,
                    managerId,
                    active);
        }
    }
}
