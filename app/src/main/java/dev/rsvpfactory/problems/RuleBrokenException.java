package dev.rsvpfactory.problems;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request breaks a rule. The API answers with a problem detail whose {@code type} ends in
 * {@link #rule()}, for example {@code group-not-found}.
 */
public class RuleBrokenException extends RuntimeException {

    private final String rule;
    private final HttpStatus status;

    public RuleBrokenException(String rule, HttpStatus status, String message) {
        super(message);
        this.rule = rule;
        this.status = status;
    }

    public String rule() {
        return rule;
    }

    public HttpStatus status() {
        return status;
    }
}
