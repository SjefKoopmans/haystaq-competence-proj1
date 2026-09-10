package nl.haystaq.tijdwijs.rules;

/**
 * Eén ontdekte businessregel: een rule-code met de soort overtreding die hij
 * oplevert, en de domeinklasse waarin hij is gevonden.
 *
 * @param className    volledige klassenaam, bv. {@code personeel.domain.Iban}
 * @param code         de rule-code zoals doorgegeven aan {@code BusinessRuleViolation}, bv. {@code iban.mod97}
 * @param kind         {@code INVALID_INPUT}, {@code CONFLICT} of {@code NOT_FOUND}
 */
public record RuleCode(String className, String code, String kind) implements Comparable<RuleCode> {

    @Override
    public int compareTo(RuleCode other) {
        int byClass = className.compareTo(other.className);
        return byClass != 0 ? byClass : code.compareTo(other.code);
    }

    public int httpStatus() {
        return switch (kind) {
            case "INVALID_INPUT" -> 400;
            case "CONFLICT" -> 409;
            case "NOT_FOUND" -> 404;
            default -> throw new IllegalStateException("onbekende kind: " + kind);
        };
    }
}
