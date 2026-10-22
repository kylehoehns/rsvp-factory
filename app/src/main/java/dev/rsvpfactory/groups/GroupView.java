package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import java.util.List;

record GroupView(long id, String name, String description, List<Member> organizers) {}
