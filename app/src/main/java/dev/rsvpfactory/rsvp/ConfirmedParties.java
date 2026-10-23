package dev.rsvpfactory.rsvp;

import java.util.List;
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

    @Override
    public List<ConfirmedParty> confirmedParties(UUID eventId) {
        return rsvps.confirmedParties(eventId);
    }
}
