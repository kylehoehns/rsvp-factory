package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import java.util.List;
import java.util.UUID;

record GroupView(UUID id, String name, String description, int memberCount, List<Member> organizers) {}
