package org.mortbay.sailing.pf.analysis;

import org.mortbay.sailing.pf.data.Race;

import java.util.List;

/**
 * Consolidated per-race derived data.
 * Held in {@link org.mortbay.sailing.pf.server.AnalysisCache}, never serialized.
 */
public record RaceDerived(
    Race race,
    int finisherCount,
    List<DivisionPf> divisionPfs  // nullable; set after PF optimisation
) {}
