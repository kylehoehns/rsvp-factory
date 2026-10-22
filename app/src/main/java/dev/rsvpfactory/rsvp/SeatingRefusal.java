package dev.rsvpfactory.rsvp;

import org.springframework.http.HttpStatus;

/**
 * A seating rule the change would break; {@link #rule()} names it the way the API's problem types do and
 * {@link #status()} is the HTTP status the API answers with.
 */
class SeatingRefusal extends RuntimeException {

    private final String rule;
    private final HttpStatus status;
    private final String reason;

    SeatingRefusal(String rule, HttpStatus status, String message) {
        super(message);
        this.rule = rule;
        this.status = status;
        this.reason = message;
    }

    String rule() {
        return rule;
    }

    HttpStatus status() {
        return status;
    }

    String reason() {
        return reason;
    }
}
