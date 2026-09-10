package nl.haystaq.tijdwijs.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fase 1 - Rule-registry: leest alle {@code BusinessRuleViolation}-codes
 * rechtstreeks uit de broncode, zodat {@code docs/discovered-rules.md} nooit
 * stiekem kan afwijken van wat de applicatie werkelijk afdwingt.
 * <p>
 * Bewust een simpele regex-scan (geen AST-parser): de aanroepstijl in dit
 * project is consistent genoeg (altijd een string-literal als laatste
 * argument) om hiermee alle 109+ codes te vinden.
 */
public final class RuleRegistryScanner {

    /** Matcht het begin van BusinessRuleViolation.<methode>( - de rest lezen we handmatig uit met paren-diepte. */
    private static final Pattern CALL_START = Pattern.compile(
            "BusinessRuleViolation\\.(invalid|conflict|notFound|require|requireState)\\(");

    private static final Pattern STRING_LITERAL = Pattern.compile("\"([a-zA-Z0-9_.]+)\"");

    private RuleRegistryScanner() {
    }

    public record Rule(String code, String kind, String location) {
    }

    /** Methode -> HTTP-kind, zie {@code BusinessRuleViolation.Kind}. */
    private static String kindFor(String method) {
        return switch (method) {
            case "invalid", "require" -> "INVALID_INPUT";
            case "conflict", "requireState" -> "CONFLICT";
            case "notFound" -> "NOT_FOUND";
            default -> throw new IllegalStateException("onbekende methode: " + method);
        };
    }

    /** Scant alle .java-bestanden onder {@code sourceRoot}, gededupliceerd en gesorteerd op code. */
    public static List<Rule> scan(Path sourceRoot) {
        Map<String, Rule> byCode = new TreeMap<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(file -> {
                String content;
                try {
                    content = Files.readString(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                String[] lines = content.split("\n", -1);
                Matcher matcher = CALL_START.matcher(content);
                while (matcher.find()) {
                    String method = matcher.group(1);
                    int argsStart = matcher.end();
                    int argsEnd = findMatchingParen(content, argsStart);
                    if (argsEnd < 0) {
                        continue;
                    }
                    String args = content.substring(argsStart, argsEnd);
                    String code = lastStringLiteral(args);
                    if (code == null) {
                        continue;
                    }
                    int line = lineNumberOf(content, matcher.start(), lines);
                    String location = sourceRoot.relativize(file).toString().replace('\\', '/') + ":" + line;
                    byCode.putIfAbsent(code, new Rule(code, kindFor(method), location));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Rule> result = new ArrayList<>(byCode.values());
        result.sort(Comparator.comparing(Rule::code));
        return result;
    }

    /** Zoekt vanaf net na de openende '(' de bijbehorende sluitende ')', paren-diepte tellend. */
    private static int findMatchingParen(String content, int openParenBodyStart) {
        int depth = 1;
        for (int i = openParenBodyStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** De laatste string-literal in de argumentenlijst is per conventie de rule-code. */
    private static String lastStringLiteral(String args) {
        Matcher m = STRING_LITERAL.matcher(args);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    private static int lineNumberOf(String content, int offset, String[] lines) {
        int count = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /** Rendert de regels als de markdown-tabel die in {@code docs/discovered-rules.md} hoort te staan. */
    public static String toMarkdown(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Discovered rules\n\n");
        sb.append("Automatisch gegenereerd door `RuleRegistryScanner` (Fase 1, F1.2) uit alle\n");
        sb.append("`BusinessRuleViolation`-aanroepen in `backend/src/main/java`. **Niet handmatig\n");
        sb.append("wijzigen** - regenereer via `RuleRegistryDocGenerator` en commit het resultaat.\n\n");
        sb.append("| Code | Kind | Vindplaats |\n");
        sb.append("| --- | --- | --- |\n");
        for (Rule rule : rules) {
            sb.append("| `").append(rule.code()).append("` | ").append(rule.kind()).append(" | `")
                    .append(rule.location()).append("` |\n");
        }
        return sb.toString();
    }
}
