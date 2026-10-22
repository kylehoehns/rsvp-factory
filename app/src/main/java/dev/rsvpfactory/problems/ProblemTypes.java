package dev.rsvpfactory.problems;

import java.net.URI;

/** Where problem types live: one URI per rule, so clients can switch on the last path segment. */
public final class ProblemTypes {

    private static final String BASE = "https://rsvp-factory.dev/problems/";

    private ProblemTypes() {}

    public static URI of(String rule) {
        return URI.create(BASE + rule);
    }
}
