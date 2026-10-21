# rsvp-factory

A community events platform: groups run events, members RSVP, full events keep a waitlist.

## Language

### People and groups

**Member**:
A person who has joined a group. Only members of a group can RSVP to its events.
_Avoid_: User, attendee, participant

**Organizer**:
A member who runs a group and its events. Any member can start a group and becomes its first organizer; organizers can make other members organizers.
_Avoid_: Admin, host, owner

**Group**:
A community that members join and organizers run; every event belongs to exactly one group.
_Avoid_: Community, chapter, meetup

### Events and seats

**Event**:
A single, scheduled gathering of a group, held at a venue. Anyone can see an event and its open seats; only members can RSVP.
_Avoid_: Meeting, session, occurrence

**Cancelled event**:
An event the organizer has called off. It stays visible, takes no RSVPs, and every RSVP to it ends.
_Avoid_: Deleted event, removed event

**Venue**:
Where an event happens, written as a free-text address.
_Avoid_: Location, place

**Capacity**:
The number of seats an event has; every event has one.
_Avoid_: Limit, max attendees, size

**Seat**:
One unit of capacity, taken by one member or one guest.
_Avoid_: Spot, slot, place

**Guest**:
A person a member brings to an event who takes a seat but isn't a member.
_Avoid_: Plus-one, +1, companion

**Guest limit**:
The most guests one RSVP may bring, set by the organizer per event.
_Avoid_: Plus-one allowance

**Party**:
A member and their guests, who hold seats together under one RSVP.
_Avoid_: Group (that means something else here), booking

### RSVPs

**RSVP**:
A member's request for seats at an event for their party; it is either confirmed or waitlisted.
_Avoid_: Booking, registration, reservation, ticket

**Confirmed**:
An RSVP whose party holds seats at the event.
_Avoid_: Going, attending, accepted

**Waitlist**:
The first-come queue of RSVPs waiting for seats at a full event.
_Avoid_: Queue, standby, backlog

**Promotion**:
Moving the earliest waitlisted RSVP whose party fits the open seats to confirmed; it happens automatically whenever seats open, without the member acting.
_Avoid_: Upgrade, offer, claim

**Open seats**:
Capacity minus the seats confirmed parties hold.
_Avoid_: Availability, free spots, vacancies

**Withdrawal**:
A member taking back their own RSVP, which frees its seats. Withdrawal is always allowed, even after the RSVP cutoff.
_Avoid_: Cancel (reserved for cancelled events), un-RSVP, drop out

**RSVP cutoff**:
The moment, set by the organizer, after which new RSVPs and promotions stop for an event; it defaults to the event's start.
_Avoid_: Deadline, close time
