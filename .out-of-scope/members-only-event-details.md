# Members-only event details

An event's details (title, description, venue, times, open seats) are visible to anyone who has its link. They are not limited to the group's members, and the venue is not hidden until someone RSVPs.

## Why this is out of scope

The spec for groups, events and RSVPs (#1) decided that reading is public and only writing needs a signed-in member: an event exists to be found and shared, and the glossary says "Anyone can see an event and its open seats; only members can RSVP."

The real risk raised in #39 was enumeration: with sequential ids, anyone could walk every group and collect every venue. That is fixed by making ids unguessable (ADR 0007, spec #65), not by hiding details from people who have the link. Hiding details would need a notion of private groups, invitations and per-viewer responses, which is a product of its own.

If private groups are ever specified, revisit this then, as part of that spec.

## Prior requests

- #39: "Event venue and description are public with no visibility control"
