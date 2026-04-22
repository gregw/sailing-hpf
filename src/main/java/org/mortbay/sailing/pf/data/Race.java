package org.mortbay.sailing.pf.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;


import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A single race. Raw layer — immutable, no derived fields.
 * <p>
 * The ID is a generated slug: clubDomain-isoDate-number, e.g. "myc.com.au-2024-11-06-0001".
 * A race may belong to more than one series; seriesIds holds all of them.
 */
public record Race(
    String id,                       // e.g. "myc.com.au-2024-11-06-0001"
    String clubId,                   // organising club website domain
    List<String> seriesIds,          // series this race contributes to (at least one)
    LocalDate date,
    int number,                      // race number within its primary series
    String name,                     // named race title, e.g. "Flinders Race"; null if unnamed
    List<Division> divisions,
    String source,                   // short importer name that created this record, e.g. "TopYacht"; nullable
    Instant lastUpdated,             // when this record was last written by an importer; nullable
    @JsonIgnore Instant loadedAt     // file modification time at load; not persisted
) implements Loadable<Race>
{

    @Override
    public Race withLoadedAt(Instant t)
    {
        return new Race(id, clubId, seriesIds, date, number, name, divisions, source, lastUpdated, t);
    }

    // loadedAt is loading metadata, not domain data — exclude from equality
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (!(o instanceof Race r))
            return false;
        return number == r.number
            && Objects.equals(id, r.id) && Objects.equals(clubId, r.clubId)
            && Objects.equals(seriesIds, r.seriesIds) && Objects.equals(date, r.date)
            && Objects.equals(name, r.name)
            && Objects.equals(divisions, r.divisions)
            && Objects.equals(source, r.source) && Objects.equals(lastUpdated, r.lastUpdated);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, clubId, seriesIds, date, number, name, divisions, source, lastUpdated);
    }

    @Override
    public String toString()
    {
        return "Race{" +
            "id='" + id + '\'' +
            ", clubId='" + clubId + '\'' +
            ", seriesIds=" + seriesIds +
            ", date=" + date +
            ", number=" + number +
            ", name='" + name + '\'' +
            ", divisions=" + divisions +
            ", source='" + source + '\'' +
            ", lastUpdated=" + lastUpdated +
            '}';
    }
}
