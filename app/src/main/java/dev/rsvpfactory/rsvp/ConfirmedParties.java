package dev.rsvpfactory.rsvp;

import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ConfirmedParties implements Rsvps {

    private final RsvpRepository rsvps;

    ConfirmedParties(RsvpRepository rsvps) {
        this.rsvps = rsvps;
    }

    @Override
    public OptionalInt confirmedPartySize(UUID eventId, UUID memberId) {
        return rsvps.confirmedPartySize(eventId, memberId);
    }
}
