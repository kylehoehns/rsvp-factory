package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import java.util.List;

/** A group as anyone sees it. */
public record GroupView(long id, String name, String description, List<Member> organizers) {}
