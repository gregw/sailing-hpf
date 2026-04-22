package org.mortbay.sailing.pf.analysis;

import java.util.List;
import java.util.Map;

/**
 * Immutable output snapshot from a single PF optimisation run.
 */
public record PfResult(
    Map<String, BoatPf> boatPfs,                          // boatId → BoatPf
    Map<String, List<DivisionPf>> divisionPfsByRaceId,    // raceId → List<DivisionPf>
    Map<String, List<EntryResidual>> residualsByBoatId,     // boatId → List<EntryResidual>
    int innerIterations,
    int outerIterations,
    PfConfig config,
    PfQuality quality                                      // null only for empty/stopped results
) {}
