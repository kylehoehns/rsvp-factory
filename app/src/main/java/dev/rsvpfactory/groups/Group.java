package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import java.util.List;

/** A group and the members who organize it. */
public record Group(long id, String name, String description, List<Member> organizers) {}
