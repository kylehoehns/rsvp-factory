package dev.rsvpfactory.attendance;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An event's attendance: each confirmed party, the seats confirmed and the people checked in. */
record AttendanceView(List<Party> parties, int seatsConfirmed, int peopleCheckedIn) {

    /** A confirmed party; {@code arrived} is null until it is checked in. */
    record Party(UUID memberId, int partySize, @Nullable Integer arrived) {}
}
