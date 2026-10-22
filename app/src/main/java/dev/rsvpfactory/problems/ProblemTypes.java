package dev.rsvpfactory.problems;

import java.net.URI;

/** Where the problem types live. They name the rule broken; clients match on the last segment. */
final class ProblemTypes {

    private static final String BASE = "https://rsvp-factory.dev/problems/";

    private ProblemTypes() {}

    static URI of(String rule) {
        return URI.create(BASE + rule);
    }
}
