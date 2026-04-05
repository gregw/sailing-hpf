package org.mortbay.sailing.hpf.analysis;

import java.util.List;

/**
 * Quality summary metrics from a single HPF optimisation run.
 * Computed at the end of the optimiser and retained in {@link org.mortbay.sailing.hpf.server.AnalysisCache}.
 */
public record HpfQuality(
    int boatsWithHpf,               // boats that received computed HPF (not just RF fallback)
    int totalEntries,               // total entry count in the optimiser
    int divisionsUsed,              // qualifying divisions
    int innerIterations,            // total ALS iterations across all outer loops
    int outerIterations,            // reweighting cycles completed
    boolean innerConverged,         // did inner loop converge (maxDelta < threshold)?
    boolean outerConverged,         // did outer loop converge (maxWeightChange < 0.01)?
    double finalMaxDelta,           // final max |Δlog(HPF)| at inner convergence
    double finalMaxWeightChange,    // final max entry weight change at outer convergence
    double medianResidual,          // median |residual| across all entries (log space)
    double iqrResidual,             // IQR of |residual| across all entries
    double pct95Residual,           // 95th percentile |residual|
    int downWeightedEntries,        // entries with final weight < 50% of initial refWeight
    int highDispersionDivisions,    // divisions where dispersion > 0.10
    double medianBoatConfidence,    // median of boat HPF weights
    List<Double> outerDeltaTrace,   // maxWeightChange at end of each outer cycle, in order
    HpfConfig config                // config used for this run
) {}
