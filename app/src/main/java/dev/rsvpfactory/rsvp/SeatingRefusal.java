package dev.rsvpfactory.rsvp;

/** A seating rule the change would break; {@link #rule()} names it the way the API's problem types do. */
class SeatingRefusal extends RuntimeException {

    private final String rule;
    private final String reason;

    SeatingRefusal(String rule, String message) {
        super(message);
        this.rule = rule;
        this.reason = message;
    }

    String rule() {
        return rule;
    }

    String reason() {
        return reason;
    }
}
