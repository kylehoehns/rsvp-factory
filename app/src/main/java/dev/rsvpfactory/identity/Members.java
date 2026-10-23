package dev.rsvpfactory.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Looks members up by id, for modules that refer to members by id only. */
public interface Members {

    Map<UUID, Member> byIds(Collection<UUID> ids);
}
