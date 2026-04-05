package org.mortbay.sailing.hpf.analysis;

import java.util.List;
import java.util.Map;

/**
 * Immutable output snapshot from a single HPF optimisation run.
 */
public record HpfResult(
    Map<String, BoatHpf> boatHpfs,                          // boatId → BoatHpf
    Map<String, List<DivisionHpf>> divisionHpfsByRaceId,    // raceId → List<DivisionHpf>
    Map<String, List<EntryResidual>> residualsByBoatId,     // boatId → List<EntryResidual>
    int innerIterations,
    int outerIterations,
    HpfConfig config,
    HpfQuality quality                                      // null only for empty/stopped results
) {}
