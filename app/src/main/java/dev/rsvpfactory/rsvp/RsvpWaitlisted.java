package dev.rsvpfactory.rsvp;

/** Published when an RSVP joins the waitlist, at {@code position} counting from 1. */
public record RsvpWaitlisted(long eventId, long memberId, int partySize, int position) {}
