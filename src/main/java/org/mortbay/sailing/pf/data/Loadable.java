package org.mortbay.sailing.pf.data;

import java.time.Instant;

/**
 * Implemented by per-file raw entities that support dirty-tracking.
 * The {@code loadedAt} field is stamped by {@code DataStore} on load and is never
 * written to JSON — it is used only to detect whether the entity has changed on disk.
 */
public interface Loadable<T>
{
    /**
     * Last-modified time of the file this entity was loaded from, or null if newly created.
     */
    Instant loadedAt();

    /**
     * Return a copy of this entity stamped with the given file modification time.
     */
    T withLoadedAt(Instant t);
}
