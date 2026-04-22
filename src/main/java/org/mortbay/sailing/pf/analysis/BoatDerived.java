package org.mortbay.sailing.pf.analysis;

import org.mortbay.sailing.pf.data.Boat;

import java.util.Set;

/**
 * Consolidated per-boat derived data: reference factors and navigation indexes.
 * Held in {@link org.mortbay.sailing.pf.server.AnalysisCache}, never serialized.
 */
public record BoatDerived(
    Boat boat,
    ReferenceFactors referenceFactors,  // nullable
    Set<String> raceIds,
    Set<String> seriesIds,
    BoatPf pf                         // nullable; set after PF optimisation
) {}
