package nl.haystaq.tijdwijs.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Leest alle {@code BusinessRuleViolation}-aanroepen uit de domeinlaag en
 * levert de gevonden rule-codes op. Dit is de bron voor
 * {@code docs/discovered-rules.md}: die documentatie kan niet meer verouderen
 * omdat ze uit de code wordt gegenereerd (zie {@link DiscoveredRulesRenderer}
 * en de drift-test die deze twee vergelijkt).
 *
 * <p>Bewust beperkt tot {@code **}{@code /domain/**}: dat is de laag die
 * Persoon A in fase 0 heeft getest (zie {@code docs/testing.md}). De ~30
 * codes in de applicatielaag ({@code *Service}) horen bij de integratietests
 * van fase 0.5 en vallen buiten deze scan.
 */
public final class RuleRegistryScanner {

    /**
     * Matcht {@code require(...)} en {@code requireState(...)} waarvan het
     * laatste argument een stringliteral is — de rule-code. De aanroep mag
     * over meerdere regels lopen; er wordt niet over een {@code ;} heen
     * gematcht, wat in deze codebase altijd het einde van de statement is.
     * Het middelste deel is bewust <em>greedy</em>: de conditie zelf kan ook
     * stringliterals bevatten (bv. {@code startsWith("NL")}), en alleen het
     * <em>laatste</em> stringliteral vóór de afsluitende {@code )} is de
     * rule-code.
     */
    private static final Pattern GUARD_CALL = Pattern.compile(
            "BusinessRuleViolation\\.(require|requireState)\\([^;]*\"([\\w.]+)\"\\s*\\)",
            Pattern.DOTALL);

    /** Matcht {@code invalid("code")}, {@code conflict("code")}, {@code notFound("code")}. */
    private static final Pattern FACTORY_CALL = Pattern.compile(
            "BusinessRuleViolation\\.(invalid|conflict|notFound)\\(\\s*\"([\\w.]+)\"\\s*\\)");

    /**
     * Matcht de delegatie in {@code Employee}: {@code validateName(value, "first_name")}.
     * De helper zelf gooit via {@code require(...)}, dus de kind is INVALID_INPUT.
     * Dit is de enige plek in de domeinlaag waar de code als variabele wordt
     * doorgegeven in plaats van als literal in de guard-aanroep zelf.
     */
    private static final Pattern DELEGATED_LITERAL = Pattern.compile(
            "validateName\\([^,]+,\\s*\"([\\w.]+)\"\\s*\\)");

    private RuleRegistryScanner() {
    }

    /**
     * Scant onder {@code root} en houdt alleen bestanden over die in een
     * {@code domain}-package staan (pad bevat {@code /domain/}). Dat is
     * bewust: de applicatielaag ({@code *Service}, {@code *Adapter}) is
     * Persoon B's fase 0.5, en hoort niet in dit gegenereerde document.
     */
    public static SortedSet<RuleCode> scan(Path root) {
        SortedSet<RuleCode> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(RuleRegistryScanner::isDomainFile)
                    .forEach(file -> found.addAll(scanFile(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static boolean isDomainFile(Path file) {
        String normalized = file.toString().replace('\\', '/');
        return normalized.contains("/domain/");
    }

    private static List<RuleCode> scanFile(Path file) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String className = classNameOf(file);
        List<RuleCode> found = new java.util.ArrayList<>();

        Matcher guard = GUARD_CALL.matcher(source);
        while (guard.find()) {
            String kind = guard.group(1).equals("requireState") ? "CONFLICT" : "INVALID_INPUT";
            found.add(new RuleCode(className, guard.group(2), kind));
        }

        Matcher factory = FACTORY_CALL.matcher(source);
        while (factory.find()) {
            String kind = switch (factory.group(1)) {
                case "conflict" -> "CONFLICT";
                case "notFound" -> "NOT_FOUND";
                default -> "INVALID_INPUT";
            };
            found.add(new RuleCode(className, factory.group(2), kind));
        }

        Matcher delegated = DELEGATED_LITERAL.matcher(source);
        while (delegated.find()) {
            found.add(new RuleCode(className, delegated.group(1), "INVALID_INPUT"));
        }

        return found;
    }

    /** Leidt {@code nl.haystaq.tijdwijs.personeel.domain.Iban} af uit het bestandspad. */
    private static String classNameOf(Path file) {
        String path = file.toString().replace('\\', '/');
        int marker = path.indexOf("java/nl/haystaq/tijdwijs/");
        String relevant = marker >= 0
                ? path.substring(marker + "java/".length())
                : path;
        String withoutExtension = relevant.substring(0, relevant.length() - ".java".length());
        return withoutExtension.replace('/', '.');
    }
}
