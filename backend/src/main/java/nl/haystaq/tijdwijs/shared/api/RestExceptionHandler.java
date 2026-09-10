package nl.haystaq.tijdwijs.shared.api;

import nl.haystaq.tijdwijs.shared.domain.BusinessRuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vertaalt domeinfouten naar HTTP, als RFC 9457 Problem Details.
 * <p>
 * De rule-code ({@code code}) is de veilige, machine-leesbare identificatie
 * van de overtreden regel (bv. {@code iban.mod97}). Dat is geen vrije tekst en
 * geen implementatiedetail: het is een stabiel contract waarop een client (of
 * een agent) mag asserteren. {@code error} en {@code ref} blijven bestaan voor
 * de bestaande frontend ({@code frontend/src/api.ts}), zodat deze fase geen
 * frontend-wijziging vereist.
 * <p>
 * Zet {@code tijdwijs.debug-rules=true} om de interne codes ook in de logs te
 * zien.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    private static final String PROBLEM_TYPE_BASE = "https://tijdwijs.haystaq.nl/problems/";

    private final boolean debugRules;

    public RestExceptionHandler(@Value("${tijdwijs.debug-rules:false}") boolean debugRules) {
        this.debugRules = debugRules;
    }

    @ExceptionHandler(BusinessRuleViolation.class)
    public ResponseEntity<Map<String, Object>> handleDomain(BusinessRuleViolation exception) {
        if (debugRules) {
            log.warn("rejected: kind={} code={}", exception.kind(), exception.code());
        }
        return switch (exception.kind()) {
            case INVALID_INPUT -> problem(HttpStatus.BAD_REQUEST, "invalid input",
                    "invalid-input", exception.code());
            case CONFLICT -> problem(HttpStatus.CONFLICT, "conflict",
                    "conflict", exception.code());
            case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "not found",
                    "not-found", exception.code());
        };
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        if (debugRules) {
            log.warn("rejected: {}", exception.getMessage());
        }
        return problem(HttpStatus.BAD_REQUEST, "invalid input", "invalid-input", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("unexpected failure ref={} ", reference, exception);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", PROBLEM_TYPE_BASE + "internal-error");
        body.put("title", "internal error");
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "internal error");
        body.put("ref", reference);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * Bouwt een RFC 9457 Problem Details-body. {@code error} blijft aanwezig
     * als alias van {@code title} voor de bestaande frontend-foutafhandeling.
     * {@code code} (de rule-code) wordt alleen meegegeven als er een domeinregel
     * is overtreden; dat is de kern van deze fase.
     */
    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String title, String typeSuffix,
                                                          String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", PROBLEM_TYPE_BASE + typeSuffix);
        body.put("title", title);
        body.put("status", status.value());
        body.put("error", title);
        if (code != null) {
            body.put("code", code);
            body.put("detail", "rule '" + code + "' was violated");
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
