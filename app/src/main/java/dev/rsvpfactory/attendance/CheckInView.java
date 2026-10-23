package dev.rsvpfactory.attendance;

import java.time.Instant;
import java.util.UUID;

/** A member's check-in: how many of their party arrived, and when it was recorded. */
record CheckInView(UUID memberId, int arrived, Instant checkedInAt) {}
