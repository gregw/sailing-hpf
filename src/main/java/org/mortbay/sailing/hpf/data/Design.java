package org.mortbay.sailing.hpf.data;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A boat design (hull type).
 * The ID is the normalised design name, e.g. "j24", "farr40".
 * Canonical name is the authoritative display form, preferably from the ORC Class field.
 * Most designs have a single maker, but some (e.g. J/24) have been built by multiple manufacturers.
 */
public record Design(
    String id,            // normalised name, e.g. "j24", "farr40"
    String canonicalName, // display name, e.g. "J/24", "Farr 40"
    List<String> makerIds, // normalised maker IDs; usually one
    List<String> aliases,  // alternate design names, e.g. "Mumm 30" for "Farr 30"
    @JsonIgnore Instant loadedAt  // file modification time at load; not persisted
) implements Loadable<Design>
{

    @Override
    public Design withLoadedAt(Instant t)
    {
        return new Design(id, canonicalName, makerIds, aliases, t);
    }

    // loadedAt is loading metadata, not domain data — exclude from equality
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (!(o instanceof Design d))
            return false;
        return Objects.equals(id, d.id) && Objects.equals(canonicalName, d.canonicalName)
            && Objects.equals(makerIds, d.makerIds) && Objects.equals(aliases, d.aliases);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, canonicalName, makerIds, aliases);
    }
}
