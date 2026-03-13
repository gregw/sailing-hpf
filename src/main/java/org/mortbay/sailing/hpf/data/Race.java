package org.mortbay.sailing.hpf.data;

import java.time.LocalDate;
import java.util.List;

/**
 * A single race. Raw layer — immutable, no derived fields.
 *
 * The ID is a generated slug: clubDomain-isoDate-hex, e.g. "myc.com.au-2024-11-06-4a1f".
 * A race may belong to more than one series; seriesIds holds all of them.
 *
 * handicapSystem is the primary system under which results were scored.
 * offsetPursuit is true for pursuit-format races.
 */
public record Race(
        String id,                       // e.g. "myc.com.au-2024-11-06-4a1f"
        String clubId,                   // organising club website domain
        List<String> seriesIds,          // series this race contributes to (at least one)
        LocalDate date,
        int number,                      // race number within its primary series
        String name,                     // named race title, e.g. "Flinders Race"; null if unnamed
        HandicapSystem handicapSystem,   // primary scoring system
        boolean offsetPursuit,           // true if pursuit-format race
        List<Division> divisions
) {}
