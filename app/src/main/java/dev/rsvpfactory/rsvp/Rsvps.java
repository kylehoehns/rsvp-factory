package dev.rsvpfactory.rsvp;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/** Asks about RSVPs, for modules that act on a member's party. */
public interface Rsvps {

    /** The size of the member's party if their RSVP to the event is confirmed; empty if waitlisted or absent. */
    OptionalInt confirmedPartySize(UUID eventId, UUID memberId);

    /** The event's confirmed parties in the order they RSVPed. */
    List<ConfirmedParty> confirmedParties(UUID eventId);
}
