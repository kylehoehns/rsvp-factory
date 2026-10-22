package dev.rsvpfactory.identity;

import java.util.Collection;
import java.util.Map;

/** Looks members up by id, for modules that refer to members by id only. */
public interface Members {

    Map<Long, Member> byIds(Collection<Long> ids);
}
