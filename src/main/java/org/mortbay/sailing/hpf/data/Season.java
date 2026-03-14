package org.mortbay.sailing.hpf.data;

import java.util.List;

/**
 * A racing season.
 * Australian seasons span the end of year, so a season label takes the form "2024-25".
 * Single calendar-year seasons use a single year label, e.g. "2024".
 * <p>
 * A season embeds its series so all club structure lives in one file per club.
 */
public record Season(
    String id,          // e.g. "2024-25" or "2024"
    List<Series> series // series run during this season
) {}
