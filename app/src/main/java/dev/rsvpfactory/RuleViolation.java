package dev.rsvpfactory;

import org.springframework.http.HttpStatus;

/**
 * A refusal: the request broke a rule. {@code rule} names the rule and becomes the problem detail's {@code type} (for
 * example {@code group-not-found}); {@code detail} tells the client what to do about it.
 */
public class RuleViolation extends RuntimeException {

    private final HttpStatus status;
    private final String rule;
    private final String detail;

    public RuleViolation(HttpStatus status, String rule, String detail) {
        super(detail);
        this.status = status;
        this.rule = rule;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public String rule() {
        return rule;
    }
}
