package org.mortbay.sailing.hpf.analysis;

import org.mortbay.sailing.hpf.data.Race;

/**
 * Consolidated per-race derived data.
 * Held in {@link org.mortbay.sailing.hpf.server.AnalysisCache}, never serialized.
 */
public record RaceDerived(
    Race race,
    int finisherCount
) {}
