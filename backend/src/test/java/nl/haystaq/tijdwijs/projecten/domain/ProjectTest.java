package nl.haystaq.tijdwijs.projecten.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertAccepted;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertConflict;
import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import nl.haystaq.tijdwijs.projecten.domain.ProjectMember.Role;
import nl.haystaq.tijdwijs.shared.domain.Hours;
import nl.haystaq.tijdwijs.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Karakteriseringstests voor het aggregate {@link Project} en de entiteit
 * {@link ProjectTask}. Patroon: zie {@code IbanTest}.
 *
 * <p>Deze klasse staat in het pakket {@code projecten.domain} omdat de
 * constructor van {@code ProjectTask} package-private is. Regels van {@code
 * ProjectTask} worden getest via {@code Project.addTask}.
 *
 * <p>Het jaartal in de projectcode moet gelijk zijn aan het startjaar
 * ({@code code.year_mismatch}), dus code en startdatum bewegen samen.
 */
@DisplayName("Project")
class ProjectTest {

    private static final UUID CLIENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID EMPLOYEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final LocalDate START = LocalDate.of(2026, 1, 5);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final ProjectCode CODE = new ProjectCode("PRJ-2026-001");
    private static final Money RATE = Money.of(new BigDecimal("105.00"));

    private static Project draft() {
        return Project.start(
                CLIENT_ID, CODE, "Migratie zaaksysteem", ProjectStatus.DRAFT, START, END, Hours.of("1800.00"), true, RATE);
    }

    /** Een actief project met één taak, zodat er geboekt kan worden. */
    private static Project active() {
        Project project = draft();
        project.addTask("Analyse", true, null);
        project.changeStatus(ProjectStatus.ACTIVE);
        return project;
    }

    @Nested
    @DisplayName("start")
    class Start {

        @Test
        @DisplayName("start een project met een eigen id")
        void startsProject() {
            Project project = draft();

            assertThat(project.id()).isNotNull();
            assertThat(project.clientId()).isEqualTo(CLIENT_ID);
            assertThat(project.code()).isEqualTo(CODE);
            assertThat(project.name()).isEqualTo("Migratie zaaksysteem");
            assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(project.tasks()).isEmpty();
            assertThat(project.members()).isEmpty();
        }

        @Test
        @DisplayName("weigert een ontbrekende opdrachtgever")
        void rejectsMissingClient() {
            assertInvalid(
                    "client_id.missing",
                    () -> Project.start(null, CODE, "Naam", ProjectStatus.DRAFT, START, END, null, false, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("weigert een ontbrekende naam; de rule-code is de veldnaam")
        void rejectsMissingName(String name) {
            assertInvalid(
                    "name",
                    () -> Project.start(CLIENT_ID, CODE, name, ProjectStatus.DRAFT, START, END, null, false, null));
        }

        @Test
        @DisplayName("weigert een naam van meer dan 120 tekens en trimt de rest")
        void rejectsTooLongName() {
            assertInvalid("name", () -> Project.start(
                    CLIENT_ID, CODE, "a".repeat(121), ProjectStatus.DRAFT, START, END, null, false, null));

            Project project = Project.start(
                    CLIENT_ID, CODE, "  Naam  ", ProjectStatus.DRAFT, START, END, null, false, null);
            assertThat(project.name()).isEqualTo("Naam");
        }

        @Test
        @DisplayName("weigert een ontbrekende startdatum")
        void rejectsMissingStartDate() {
            assertInvalid("start_date.missing", () -> Project.start(
                    CLIENT_ID, CODE, "Naam", ProjectStatus.DRAFT, null, END, null, false, null));
        }

        @Test
        @DisplayName("weigert een einddatum vóór de startdatum")
        void rejectsEndBeforeStart() {
            assertInvalid("end_date.order", () -> Project.start(
                    CLIENT_ID, CODE, "Naam", ProjectStatus.DRAFT, START, START.minusDays(1), null, false, null));
        }

        @Test
        @DisplayName("staat een einddatum gelijk aan de startdatum toe")
        void allowsEndEqualToStart() {
            assertAccepted(() -> Project.start(
                    CLIENT_ID, CODE, "Naam", ProjectStatus.DRAFT, START, START, null, false, null));
        }

        @Test
        @DisplayName("weigert een code waarvan het jaartal niet bij de startdatum past")
        void rejectsYearMismatch() {
            assertInvalid("code.year_mismatch", () -> Project.start(
                    CLIENT_ID,
                    new ProjectCode("PRJ-2025-001"),
                    "Naam",
                    ProjectStatus.DRAFT,
                    START,
                    END,
                    null,
                    false,
                    null));
        }

        @Test
        @DisplayName("weigert een declarabel project zonder standaardtarief")
        void rejectsBillableWithoutRate() {
            assertInvalid("billable.rate_required", () -> Project.start(
                    CLIENT_ID, CODE, "Naam", ProjectStatus.DRAFT, START, END, null, true, null));
        }

        @Test
        @DisplayName("een niet-declarabel project mag zonder tarief")
        void allowsNonBillableWithoutRate() {
            assertAccepted(() -> Project.start(
                    CLIENT_ID, CODE, "Naam", ProjectStatus.DRAFT, START, END, null, false, null));
        }

        @Test
        @DisplayName("een ontbrekende status wordt DRAFT")
        void nullStatusBecomesDraft() {
            Project project =
                    Project.start(CLIENT_ID, CODE, "Naam", null, START, END, null, false, null);

            assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
        }

        @Test
        @DisplayName("een project kan direct actief worden gestart, zonder taken")
        void mayStartActiveWithoutTasks() {
            // KARAKTERISERING: start() controleert project.no_tasks niet, terwijl
            // changeStatus(ACTIVE) dat wel doet. Een actief project zonder taken is
            // dus wel aan te maken, maar niet via een statuswijziging.
            Project project = Project.start(
                    CLIENT_ID, CODE, "Naam", ProjectStatus.ACTIVE, START, END, null, false, null);

            assertThat(project.status()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(project.tasks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addTask")
    class AddTask {

        @Test
        @DisplayName("voegt een taak toe en levert hem op")
        void addsTask() {
            Project project = draft();

            ProjectTask task = project.addTask("Analyse", true, null);

            assertThat(task.id()).isNotNull();
            assertThat(task.name()).isEqualTo("Analyse");
            assertThat(task.isBillable()).isTrue();
            assertThat(task.isArchived()).isFalse();
            assertThat(project.tasks()).containsExactly(task);
            assertThat(project.task(task.id())).contains(task);
        }

        @Test
        @DisplayName("weigert een taak op een afgesloten project")
        void rejectsTaskOnClosedProject() {
            Project project = active();
            project.changeStatus(ProjectStatus.CLOSED);

            assertConflict("project.closed", () -> project.addTask("Realisatie", true, null));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" is een duplicaat van \"Analyse\"")
        @ValueSource(strings = {"Analyse", "analyse", "ANALYSE", "  Analyse  "})
        @DisplayName("weigert een dubbele taaknaam, ongeacht kapitalisatie en witruimte")
        void rejectsDuplicateName(String duplicate) {
            Project project = draft();
            project.addTask("Analyse", true, null);

            assertConflict("task.duplicate", () -> project.addTask(duplicate, true, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("weigert een ontbrekende taaknaam")
        void rejectsMissingName(String name) {
            Project project = draft();

            assertInvalid("name", () -> project.addTask(name, true, null));
        }

        @Test
        @DisplayName("weigert een taaknaam van meer dan 80 tekens")
        void rejectsTooLongName() {
            Project project = draft();

            assertInvalid("name", () -> project.addTask("a".repeat(81), true, null));
            assertAccepted(() -> project.addTask("a".repeat(80), true, null));
        }

        @Test
        @DisplayName("weigert een afwijkend tarief op een niet-declarabele taak")
        void rejectsRateOverrideOnNonBillableTask() {
            Project project = draft();

            assertInvalid("rate_override.not_billable", () -> project.addTask("Intern overleg", false, RATE));
        }

        @Test
        @DisplayName("staat een afwijkend tarief toe op een declarabele taak")
        void allowsRateOverrideOnBillableTask() {
            Project project = draft();

            ProjectTask task = project.addTask("Realisatie", true, Money.of(new BigDecimal("115.00")));

            assertThat(task.rateOverride().amount()).isEqualByComparingTo("115.00");
        }

        @Test
        @DisplayName("de duplicaatcontrole komt vóór de naamcontrole")
        void duplicateCheckComesFirst() {
            // KARAKTERISERING: een leeg project met een taak "" bestaat niet, maar
            // de duplicaatcontrole vergelijkt null met "" en slaat als eerste toe
            // zodra er al een naamloze taak zou zijn. Bij een leeg project valt de
            // naamcontrole toe.
            Project project = draft();

            assertInvalid("name", () -> project.addTask(null, true, null));
        }

        @Test
        @DisplayName("een gearchiveerde taak blijft de naam bezet houden")
        void archivedTaskStillBlocksName() {
            // KARAKTERISERING: archive() haalt de taak niet uit de lijst, dus de
            // naam blijft bezet voor de duplicaatcontrole.
            Project project = draft();
            ProjectTask task = project.addTask("Analyse", true, null);
            task.archive();

            assertThat(task.isArchived()).isTrue();
            assertConflict("task.duplicate", () -> project.addTask("Analyse", true, null));
        }
    }

    @Nested
    @DisplayName("assignMember")
    class AssignMember {

        @Test
        @DisplayName("voegt een teamlid toe")
        void assignsMember() {
            Project project = draft();

            project.assignMember(EMPLOYEE_ID, Role.MEMBER);

            assertThat(project.hasMember(EMPLOYEE_ID)).isTrue();
            assertThat(project.hasLead(EMPLOYEE_ID)).isFalse();
            assertThat(project.members()).hasSize(1);
        }

        @Test
        @DisplayName("een tweede toewijzing overschrijft de rol in plaats van te dupliceren")
        void reassignmentIsUpsert() {
            // KARAKTERISERING: assignMember verwijdert eerst een bestaand lid met
            // hetzelfde id. Het is dus een upsert, geen add.
            Project project = draft();
            project.assignMember(EMPLOYEE_ID, Role.MEMBER);

            project.assignMember(EMPLOYEE_ID, Role.LEAD);

            assertThat(project.members()).hasSize(1);
            assertThat(project.hasLead(EMPLOYEE_ID)).isTrue();
        }

        @Test
        @DisplayName("een ontbrekende rol wordt MEMBER")
        void nullRoleBecomesMember() {
            Project project = draft();

            project.assignMember(EMPLOYEE_ID, null);

            assertThat(project.hasMember(EMPLOYEE_ID)).isTrue();
            assertThat(project.hasLead(EMPLOYEE_ID)).isFalse();
        }

        @Test
        @DisplayName("weigert een teamlid op een afgesloten project")
        void rejectsMemberOnClosedProject() {
            Project project = active();
            project.changeStatus(ProjectStatus.CLOSED);

            assertConflict("project.closed", () -> project.assignMember(EMPLOYEE_ID, Role.MEMBER));
        }

        @Test
        @DisplayName("weigert een ontbrekend medewerker-id")
        void rejectsMissingEmployeeId() {
            Project project = draft();

            assertInvalid("employee_id.missing", () -> project.assignMember(null, Role.MEMBER));
        }

        @Test
        @DisplayName("hasMember en hasLead zijn onwaar voor een onbekende medewerker")
        void unknownEmployeeIsNotMember() {
            Project project = draft();

            assertThat(project.hasMember(UUID.randomUUID())).isFalse();
            assertThat(project.hasLead(UUID.randomUUID())).isFalse();
        }
    }

    @Nested
    @DisplayName("changeStatus")
    class ChangeStatus {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
            "DRAFT,   ON_HOLD",
            "DRAFT,   CLOSED",
            "ACTIVE,  DRAFT",
            "ON_HOLD, DRAFT",
            "CLOSED,  DRAFT",
            "CLOSED,  ACTIVE",
            "CLOSED,  ON_HOLD"
        })
        @DisplayName("weigert een niet-toegestane overgang")
        void rejectsForbiddenTransition(ProjectStatus from, ProjectStatus to) {
            Project project = projectIn(from);

            assertConflict("status.transition", () -> project.changeStatus(to));
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({"ACTIVE, ON_HOLD", "ACTIVE, CLOSED", "ON_HOLD, ACTIVE", "ON_HOLD, CLOSED"})
        @DisplayName("staat een toegestane overgang toe")
        void allowsPermittedTransition(ProjectStatus from, ProjectStatus to) {
            // projectIn() levert voor ACTIVE en ON_HOLD een project met een taak op,
            // zodat de overgang naar ACTIVE niet op project.no_tasks stuit.
            Project project = projectIn(from);

            project.changeStatus(to);

            assertThat(project.status()).isEqualTo(to);
        }

        @Test
        @DisplayName("DRAFT naar ACTIVE lukt zodra er een taak is")
        void draftToActiveWithTask() {
            Project project = draft();
            project.addTask("Analyse", true, null);

            project.changeStatus(ProjectStatus.ACTIVE);

            assertThat(project.status()).isEqualTo(ProjectStatus.ACTIVE);
        }

        @Test
        @DisplayName("weigert activeren zonder taken")
        void rejectsActivationWithoutTasks() {
            Project project = draft();

            assertConflict("project.no_tasks", () -> project.changeStatus(ProjectStatus.ACTIVE));
        }

        @Test
        @DisplayName("staat activeren toe met minstens één taak")
        void allowsActivationWithTasks() {
            Project project = draft();
            project.addTask("Analyse", true, null);

            assertAccepted(() -> project.changeStatus(ProjectStatus.ACTIVE));
        }

        @Test
        @DisplayName("de overgangscontrole komt vóór de takencontrole")
        void transitionCheckComesFirst() {
            Project project = active();
            project.changeStatus(ProjectStatus.CLOSED);

            assertConflict("status.transition", () -> project.changeStatus(ProjectStatus.ACTIVE));
        }

        @ParameterizedTest(name = "[{index}] {0} naar zichzelf")
        @EnumSource(ProjectStatus.class)
        @DisplayName("dezelfde status opnieuw zetten is toegestaan")
        void selfTransitionAllowed(ProjectStatus status) {
            Project project = projectIn(status);

            assertAccepted(() -> project.changeStatus(status));
        }

        @Test
        @DisplayName("een ontbrekende status geeft een NullPointerException, geen rule-code")
        void nullStatusThrowsNpe() {
            // KARAKTERISERING: allowedNext() levert een Set.of(...) op, en die gooit
            // een NPE bij contains(null) in plaats van false terug te geven. De
            // status.transition-regel wordt dus nooit bereikt en een agent ziet
            // geen rule-code. Zie docs/testing.md.
            Project project = draft();

            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> project.changeStatus(null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("reschedule")
    class Reschedule {

        @Test
        @DisplayName("verschuift de looptijd")
        void reschedules() {
            Project project = draft();

            project.reschedule(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30));

            assertThat(project.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(project.endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        }

        @Test
        @DisplayName("staat een open einde toe")
        void allowsOpenEnd() {
            Project project = draft();

            project.reschedule(START, null);

            assertThat(project.endDate()).isNull();
        }

        @Test
        @DisplayName("weigert een ontbrekende startdatum")
        void rejectsMissingStart() {
            Project project = draft();

            assertInvalid("start_date.missing", () -> project.reschedule(null, END));
        }

        @Test
        @DisplayName("weigert een einddatum vóór de startdatum")
        void rejectsEndBeforeStart() {
            Project project = draft();

            assertInvalid("end_date.order", () -> project.reschedule(START, START.minusDays(1)));
        }

        @Test
        @DisplayName("weigert een startdatum in een ander jaar dan de code")
        void rejectsYearMismatch() {
            Project project = draft();

            assertInvalid("code.year_mismatch", () -> project.reschedule(LocalDate.of(2027, 1, 4), null));
        }

        @Test
        @DisplayName("laat het project onaangeroerd als een regel faalt")
        void leavesStateUntouchedOnFailure() {
            Project project = draft();

            assertInvalid("code.year_mismatch", () -> project.reschedule(LocalDate.of(2027, 1, 4), null));

            assertThat(project.startDate()).isEqualTo(START);
            assertThat(project.endDate()).isEqualTo(END);
        }

        @Test
        @DisplayName("verschuiven kan ook op een afgesloten project")
        void allowedOnClosedProject() {
            // KARAKTERISERING: reschedule controleert de status niet, anders dan
            // addTask en assignMember die project.closed bewaken.
            Project project = active();
            project.changeStatus(ProjectStatus.CLOSED);

            assertAccepted(() -> project.reschedule(START, END));
        }
    }

    @Nested
    @DisplayName("changeCommercials")
    class ChangeCommercials {

        @Test
        @DisplayName("wijzigt budget, declarabiliteit en tarief")
        void changesCommercials() {
            Project project = draft();
            Money newRate = Money.of(new BigDecimal("120.00"));

            project.changeCommercials(Hours.of("2000.00"), true, newRate);

            assertThat(project.budgetHours()).isEqualTo(Hours.of("2000.00"));
            assertThat(project.isBillable()).isTrue();
            assertThat(project.defaultRate()).isEqualTo(newRate);
        }

        @Test
        @DisplayName("weigert declarabel zonder tarief")
        void rejectsBillableWithoutRate() {
            Project project = draft();

            assertInvalid("billable.rate_required", () -> project.changeCommercials(null, true, null));
        }

        @Test
        @DisplayName("staat niet-declarabel zonder tarief toe")
        void allowsNonBillableWithoutRate() {
            Project project = draft();

            project.changeCommercials(null, false, null);

            assertThat(project.isBillable()).isFalse();
            assertThat(project.budgetHours()).isNull();
            assertThat(project.defaultRate()).isNull();
        }
    }

    @Nested
    @DisplayName("assertBookableOn")
    class BookableOn {

        @Test
        @DisplayName("staat boeken toe binnen de looptijd van een actief project")
        void allowsBookingWithinPeriod() {
            Project project = active();

            assertAccepted(() -> project.assertBookableOn(START));
            assertAccepted(() -> project.assertBookableOn(END));
            assertAccepted(() -> project.assertBookableOn(LocalDate.of(2026, 6, 1)));
        }

        @ParameterizedTest(name = "[{index}] status {0} blokkeert boeken")
        @EnumSource(
                value = ProjectStatus.class,
                names = {"DRAFT", "ON_HOLD", "CLOSED"})
        @DisplayName("weigert boeken op een project dat niet actief is")
        void rejectsBookingWhenNotActive(ProjectStatus status) {
            Project project = projectIn(status);

            assertConflict("project.not_active", () -> project.assertBookableOn(LocalDate.of(2026, 6, 1)));
        }

        @Test
        @DisplayName("weigert boeken vóór de startdatum")
        void rejectsBeforeStart() {
            Project project = active();

            assertConflict("work_date.before_project", () -> project.assertBookableOn(START.minusDays(1)));
        }

        @Test
        @DisplayName("weigert boeken na de einddatum")
        void rejectsAfterEnd() {
            Project project = active();

            assertConflict("work_date.after_project", () -> project.assertBookableOn(END.plusDays(1)));
        }

        @Test
        @DisplayName("een project zonder einddatum blijft boekbaar")
        void openEndedStaysBookable() {
            Project project = draft();
            project.addTask("Analyse", true, null);
            project.reschedule(START, null);
            project.changeStatus(ProjectStatus.ACTIVE);

            assertAccepted(() -> project.assertBookableOn(LocalDate.of(2099, 1, 1)));
        }

        @Test
        @DisplayName("de statuscontrole komt vóór de datumcontroles")
        void statusCheckComesFirst() {
            Project project = draft();

            assertConflict("project.not_active", () -> project.assertBookableOn(START.minusDays(1)));
        }
    }

    @Nested
    @DisplayName("collecties")
    class Collections {

        @Test
        @DisplayName("tasks en members geven een onwijzigbare kopie")
        void collectionsAreImmutable() {
            Project project = draft();
            project.addTask("Analyse", true, null);
            project.assignMember(EMPLOYEE_ID, Role.LEAD);

            assertThat(project.tasks()).isUnmodifiable();
            assertThat(project.members()).isUnmodifiable();
        }

        @Test
        @DisplayName("task geeft leeg terug voor een onbekend id")
        void unknownTaskIsEmpty() {
            assertThat(draft().task(UUID.randomUUID())).isEmpty();
        }
    }

    /** Brengt een project in de gevraagde status, voor de statusmatrixtests. */
    private static Project projectIn(ProjectStatus status) {
        return switch (status) {
            case DRAFT -> draft();
            case ACTIVE -> active();
            case ON_HOLD -> {
                Project project = active();
                project.changeStatus(ProjectStatus.ON_HOLD);
                yield project;
            }
            case CLOSED -> {
                Project project = active();
                project.changeStatus(ProjectStatus.CLOSED);
                yield project;
            }
        };
    }
}
