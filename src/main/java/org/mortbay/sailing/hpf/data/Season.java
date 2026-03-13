package org.mortbay.sailing.hpf.data;

/**
 * A racing season.
 * Australian seasons span the end of year, so a season label takes the form "2024-25".
 * Single calendar-year seasons use a single year label, e.g. "2024".
 */
public record Season(
        String id // e.g. "2024-25" or "2024"
) {}
