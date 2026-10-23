package dev.rsvpfactory.attendance;

import org.jspecify.annotations.Nullable;

/** How many of the party came; absent means the whole party. */
record CheckInRequest(@Nullable Integer arrived) {}
