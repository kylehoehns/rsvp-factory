package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** Published when an RSVP joins the waitlist, at {@code position} counting from 1. */
public record RsvpWaitlisted(long eventId, UUID memberId, int partySize, int position) {}
