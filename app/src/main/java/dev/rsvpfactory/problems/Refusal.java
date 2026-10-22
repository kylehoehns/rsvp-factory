package dev.rsvpfactory.problems;

import org.springframework.http.HttpStatus;

/** A request refused because it breaks a rule. {@code rule} becomes the last segment of the problem's {@code type}. */
public class Refusal extends RuntimeException {

    private final HttpStatus status;
    private final String rule;

    public Refusal(HttpStatus status, String rule, String detail) {
        super(detail);
        this.status = status;
        this.rule = rule;
    }

    public HttpStatus status() {
        return status;
    }

    public String rule() {
        return rule;
    }
}
