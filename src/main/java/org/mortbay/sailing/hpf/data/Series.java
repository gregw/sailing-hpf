package org.mortbay.sailing.hpf.data;

/**
 * A named series of races at a club within a season.
 * The ID follows the pattern: clubDomain/seasonId/normalised-series-name,
 * e.g. "myc.com.au/2024-25/wednesday-twilight".
 *
 * Each club has a catch-all series (isCatchAll = true) for standalone races that
 * do not belong to any real series. Every Race belongs to at least one Series.
 */
public record Series(
        String id,         // e.g. "myc.com.au/2024-25/wednesday-twilight"
        String clubId,     // website domain of the organising club
        String seasonId,   // e.g. "2024-25"
        String name,       // human-readable name, e.g. "Wednesday Twilight"
        boolean isCatchAll // true for the pseudo-series holding standalone races
) {}
