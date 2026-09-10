package nl.haystaq.tijdwijs.projecten.domain;

import static nl.haystaq.tijdwijs.testsupport.Violations.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import nl.haystaq.tijdwijs.projecten.domain.ProjectMember.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Karakteriseringstests voor {@link ProjectMember}. Patroon: zie {@code IbanTest}. */
@DisplayName("ProjectMember")
class ProjectMemberTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");

    @Nested
    @DisplayName("employee_id.missing")
    class MissingEmployee {

        @Test
        @DisplayName("weigert een ontbrekend medewerker-id")
        void rejectsNullEmployeeId() {
            assertInvalid("employee_id.missing", () -> new ProjectMember(null, Role.MEMBER));
        }

        @Test
        @DisplayName("weigert null ook als de rol ontbreekt")
        void rejectsNullEmployeeIdWithNullRole() {
            assertInvalid("employee_id.missing", () -> new ProjectMember(null, null));
        }
    }

    @Nested
    @DisplayName("Role.parse")
    class RoleParsing {

        @Test
        @DisplayName("null wordt MEMBER in plaats van een fout")
        void nullBecomesMember() {
            // KARAKTERISERING: stilzwijgende default, net als ProjectStatus.parse.
            assertThat(Role.parse(null)).isEqualTo(Role.MEMBER);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> role.unknown")
        @ValueSource(strings = {"", "OWNER", "MANAGER", "LEIDER", "  "})
        @DisplayName("weigert een onbekende rol")
        void rejectsUnknown(String raw) {
            assertInvalid("role.unknown", () -> Role.parse(raw));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(Role.class)
        @DisplayName("herkent elke bestaande rol")
        void parsesAllValues(Role role) {
            assertThat(Role.parse(role.name())).isEqualTo(role);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({"lead, LEAD", "LeAd, LEAD", "' LEAD ', LEAD", "member, MEMBER"})
        @DisplayName("trimt en negeert kapitalisatie")
        void trimsAndIgnoresCase(String raw, Role expected) {
            assertThat(Role.parse(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("constructor")
    class Construction {

        @ParameterizedTest(name = "[{index}] rol {0}")
        @EnumSource(Role.class)
        @DisplayName("bewaart medewerker en rol")
        void keepsEmployeeAndRole(Role role) {
            ProjectMember member = new ProjectMember(EMPLOYEE_ID, role);

            assertThat(member.employeeId()).isEqualTo(EMPLOYEE_ID);
            assertThat(member.role()).isEqualTo(role);
        }

        @Test
        @DisplayName("een ontbrekende rol wordt MEMBER")
        void nullRoleBecomesMember() {
            assertThat(new ProjectMember(EMPLOYEE_ID, null).role()).isEqualTo(Role.MEMBER);
        }
    }

    @Nested
    @DisplayName("waardegelijkheid")
    class Equality {

        @Test
        @DisplayName("gelijk bij dezelfde medewerker en rol")
        void equalOnSameEmployeeAndRole() {
            ProjectMember a = new ProjectMember(EMPLOYEE_ID, Role.LEAD);
            ProjectMember b = new ProjectMember(EMPLOYEE_ID, Role.LEAD);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("ongelijk bij een andere rol")
        void notEqualOnDifferentRole() {
            // KARAKTERISERING: de rol maakt deel uit van de gelijkheid. Twee
            // ProjectMember-objecten voor dezelfde medewerker met een andere rol
            // zijn dus verschillend. Project.assignMember lost dat op met een
            // upsert; zie ProjectTest.
            assertThat(new ProjectMember(EMPLOYEE_ID, Role.LEAD))
                    .isNotEqualTo(new ProjectMember(EMPLOYEE_ID, Role.MEMBER));
        }

        @Test
        @DisplayName("ongelijk bij een andere medewerker")
        void notEqualOnDifferentEmployee() {
            assertThat(new ProjectMember(EMPLOYEE_ID, Role.MEMBER))
                    .isNotEqualTo(new ProjectMember(UUID.randomUUID(), Role.MEMBER));
        }

        @Test
        @DisplayName("niet gelijk aan null of een ander type")
        void notEqualToOtherTypes() {
            assertThat(new ProjectMember(EMPLOYEE_ID, Role.MEMBER))
                    .isNotEqualTo(null)
                    .isNotEqualTo(EMPLOYEE_ID);
        }
    }
}
