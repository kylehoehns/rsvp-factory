package dev.rsvpfactory.identity;

/** The member making the current request. */
public interface CurrentMember {

    /** The signed-in member; the API only reaches callers of this once sign-in is required. */
    Member get();
}
