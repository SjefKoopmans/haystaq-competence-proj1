package nl.haystaq.tijdwijs.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bewaakt dat {@code docs/discovered-rules.md} overeenkomt met de rule-codes
 * die daadwerkelijk in {@code backend/src/main/java/**}{@code /domain/**}
 * staan.
 *
 * <p>Dit is de drift-gate uit fase 1 (zie {@code docs/agentic-workflow-plan.md}
 * §5, §7.3): documentatie over businessregels kan niet meer verouderen omdat
 * ze wordt gegenereerd, en deze test faalt zodra de code en het document uit
 * elkaar lopen. Draait automatisch mee in {@code mvnw test} en dus in elke
 * CI-run die dat aanroept — er is geen aparte workflow-stap nodig.
 *
 * <p>Regenereren na een bewuste regelwijziging:
 * <pre>
 * mvnw test -Dtest=DiscoveredRulesDriftTest -Dtijdwijs.rules.write=true
 * </pre>
 */
@DisplayName("docs/discovered-rules.md (drift-gate)")
class DiscoveredRulesDriftTest {

    private static final Path DOMAIN_ROOT =
            Path.of("src", "main", "java", "nl", "haystaq", "tijdwijs");
    private static final Path DISCOVERED_RULES_MD =
            Path.of("..", "docs", "discovered-rules.md");

    @Test
    @DisplayName("het gegenereerde document komt overeen met de code")
    void discoveredRulesMatchesCode() throws IOException {
        SortedSet<RuleCode> rules = RuleRegistryScanner.scan(DOMAIN_ROOT);
        assertThat(rules).as("er moeten rule-codes gevonden zijn in de domeinlaag").isNotEmpty();

        String generated = DiscoveredRulesRenderer.render(rules);

        if (Boolean.getBoolean("tijdwijs.rules.write")) {
            Files.writeString(DISCOVERED_RULES_MD, generated, StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(DISCOVERED_RULES_MD))
                .as("docs/discovered-rules.md ontbreekt; genereer met "
                        + "-Dtijdwijs.rules.write=true")
                .isTrue();
        String onDisk = Files.readString(DISCOVERED_RULES_MD, StandardCharsets.UTF_8);

        assertThat(normalize(onDisk))
                .as("docs/discovered-rules.md is niet meer in sync met de domeinlaag. "
                        + "Regenereer met: mvnw test -Dtest=DiscoveredRulesDriftTest "
                        + "-Dtijdwijs.rules.write=true")
                .isEqualTo(normalize(generated));
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").strip();
    }
}
