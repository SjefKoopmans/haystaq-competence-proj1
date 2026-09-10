package nl.haystaq.tijdwijs.rules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Rendert een {@link RuleCode}-verzameling naar de inhoud van
 * {@code docs/discovered-rules.md}.
 *
 * <p>Puur feitelijk: code, HTTP-status en de klasse waarin hij is gevonden.
 * Geen betekenisuitleg — die zou zelf weer kunnen verouderen. De code én de
 * bron zijn het enige dat de generator kan garanderen dat klopt.
 */
public final class DiscoveredRulesRenderer {

    private DiscoveredRulesRenderer() {
    }

    public static String render(SortedSet<RuleCode> rules) {
        StringBuilder out = new StringBuilder();
        out.append("# Discovered rules\n\n");
        out.append("> **Gegenereerd bestand — niet handmatig wijzigen.** Gemaakt door ")
                .append("`RuleRegistryScanner` op basis van `BusinessRuleViolation`-aanroepen in ")
                .append("`backend/src/main/java/**/domain/**`. Regenereer met de test ")
                .append("`DiscoveredRulesDriftTest` (draait mee in `mvnw test`).\n>\n");
        out.append("> Scope: alleen de domeinlaag (~79 codes). De ~30 codes in de ")
                .append("applicatielaag (`*Service`) staan hier niet in — die zijn fase 0.5 ")
                .append("(Persoon B, integratietests).\n\n");
        out.append("Totaal: ").append(rules.size()).append(" rule-codes in ")
                .append(distinctClasses(rules)).append(" klassen.\n\n");
        out.append("| Klasse | Code | Kind | HTTP |\n");
        out.append("| --- | --- | --- | --- |\n");
        for (RuleCode rule : rules) {
            out.append("| `").append(shortName(rule.className())).append("` | `")
                    .append(rule.code()).append("` | ").append(rule.kind()).append(" | ")
                    .append(rule.httpStatus()).append(" |\n");
        }
        return out.toString();
    }

    private static long distinctClasses(SortedSet<RuleCode> rules) {
        SortedSet<String> classes = new TreeSet<>();
        rules.forEach(r -> classes.add(r.className()));
        return classes.size();
    }

    /** {@code nl.haystaq.tijdwijs.personeel.domain.Iban} -> {@code personeel.domain.Iban}. */
    private static String shortName(String className) {
        String prefix = "nl.haystaq.tijdwijs.";
        return className.startsWith(prefix) ? className.substring(prefix.length()) : className;
    }

    /** Voor eventueel hergebruik: groepeert per klasse, in volgorde van eerste voorkomen. */
    public static Map<String, java.util.List<RuleCode>> groupByClass(SortedSet<RuleCode> rules) {
        Map<String, java.util.List<RuleCode>> byClass = new LinkedHashMap<>();
        for (RuleCode rule : rules) {
            byClass.computeIfAbsent(rule.className(), k -> new java.util.ArrayList<>()).add(rule);
        }
        return byClass;
    }
}
