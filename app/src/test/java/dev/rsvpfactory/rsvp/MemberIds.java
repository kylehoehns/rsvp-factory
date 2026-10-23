package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** Stand-in member ids for seating tests: the same number is always the same member. */
final class MemberIds {

    private MemberIds() {}

    static UUID member(int number) {
        return new UUID(0x0197a00000007000L, 0x8000000000000000L + number);
    }
}
