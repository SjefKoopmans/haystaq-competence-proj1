package nl.haystaq.tijdwijs.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import nl.haystaq.tijdwijs.shared.domain.BusinessRuleViolation;
import nl.haystaq.tijdwijs.shared.domain.BusinessRuleViolation.Kind;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Assertie-helper voor domeinregels. Dit is het centrale patroon van de
 * testsuite: elke karakteriseringstest gebruikt {@code assertViolation}.
 *
 * <p>Er wordt altijd op zowel {@link BusinessRuleViolation#code()} als
 * {@link BusinessRuleViolation#kind()} geasserteerd. Dat is bewust:
 *
 * <ul>
 *   <li>De {@code code} is de machineleesbare afspraak. Nooit op de
 *       foutmelding asserteren; die is vrije tekst en breekt bij elke typo-fix.
 *   <li>De {@code kind} bepaalt de HTTP-status (400/409/404). Een regel die
 *       stilletjes van {@code require} naar {@code requireState} verschuift,
 *       verandert het API-contract zonder dat de code wijzigt. Alleen een
 *       assertie op {@code kind} vangt dat.
 * </ul>
 */
public final class Violations {

    private Violations() {}

    /** Verwacht een overtreding met exact deze code en soort. */
    public static void assertViolation(String expectedCode, Kind expectedKind, ThrowingCallable action) {
        BusinessRuleViolation violation = catchThrowableOfType(BusinessRuleViolation.class, action);

        assertThat(violation)
                .as("verwachtte een BusinessRuleViolation met code '%s', maar er werd niets gegooid", expectedCode)
                .isNotNull();
        assertThat(violation.code()).as("rule-code").isEqualTo(expectedCode);
        assertThat(violation.kind())
                .as("soort overtreding voor code '%s'", expectedCode)
                .isEqualTo(expectedKind);
    }

    /**
     * Verwacht een overtreding met deze code en soort {@link Kind#INVALID_INPUT}
     * (HTTP 400). Dit is wat {@code BusinessRuleViolation.require} gooit.
     */
    public static void assertInvalid(String expectedCode, ThrowingCallable action) {
        assertViolation(expectedCode, Kind.INVALID_INPUT, action);
    }

    /**
     * Verwacht een overtreding met deze code en soort {@link Kind#CONFLICT}
     * (HTTP 409). Dit is wat {@code BusinessRuleViolation.requireState} gooit.
     */
    public static void assertConflict(String expectedCode, ThrowingCallable action) {
        assertViolation(expectedCode, Kind.CONFLICT, action);
    }

    /** Verwacht een overtreding met deze code en soort {@link Kind#NOT_FOUND} (HTTP 404). */
    public static void assertNotFound(String expectedCode, ThrowingCallable action) {
        assertViolation(expectedCode, Kind.NOT_FOUND, action);
    }

    /**
     * Verwacht dat er géén domeinregel wordt overtreden. Even belangrijk als de
     * negatieve gevallen: zonder dit zou een test slagen omdat álles faalt.
     */
    public static void assertAccepted(ThrowingCallable action) {
        assertThatCode(action).as("verwachtte geen BusinessRuleViolation").doesNotThrowAnyException();
    }
}
