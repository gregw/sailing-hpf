package org.mortbay.sailing.hpf.analysis;

import org.mortbay.sailing.hpf.data.Boat;

import java.util.Set;

/**
 * Consolidated per-boat derived data: reference factors and navigation indexes.
 * Held in {@link org.mortbay.sailing.hpf.server.AnalysisCache}, never serialized.
 */
public record BoatDerived(
    Boat boat,
    ReferenceFactors referenceFactors,  // nullable
    Set<String> raceIds,
    Set<String> seriesIds
) {}
