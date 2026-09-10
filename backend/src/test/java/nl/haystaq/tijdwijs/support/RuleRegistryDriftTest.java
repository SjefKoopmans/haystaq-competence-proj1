package nl.haystaq.tijdwijs.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Fase 1 - Drift-check (F1.2): {@code docs/discovered-rules.md} is een
 * <em>gegenereerd</em> bestand. Deze test faalt in CI zodra iemand een
 * {@code BusinessRuleViolation}-code toevoegt/verwijdert/hernoemt in
 * {@code domain}/{@code application} zonder het bestand te regenereren.
 * <p>
 * Regenereren: {@code RuleRegistryScanner.toMarkdown(RuleRegistryScanner.scan(sourceRoot))}
 * schrijven naar {@code docs/discovered-rules.md} en committen.
 */
class RuleRegistryDriftTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path DISCOVERED_RULES = Path.of("..", "docs", "discovered-rules.md");

    @Test
    void discoveredRulesDocMatchesSourceCode() throws IOException {
        List<RuleRegistryScanner.Rule> rules = RuleRegistryScanner.scan(SOURCE_ROOT);
        String expected = RuleRegistryScanner.toMarkdown(rules);

        if (Files.notExists(DISCOVERED_RULES)) {
            fail("docs/discovered-rules.md ontbreekt. Genereer het bestand (zie RuleRegistryScanner) en commit het.");
        }
        String actual = Files.readString(DISCOVERED_RULES);

        assertEquals(normalize(expected), normalize(actual),
                "docs/discovered-rules.md is gedrift van de broncode. Regenereer het bestand met "
                        + "RuleRegistryScanner en commit opnieuw.");
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").strip();
    }
}
