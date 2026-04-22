package org.mortbay.sailing.pf.analysis;

import java.time.LocalDate;

/**
 * Per-entry residual for charting a boat's consistency across races.
 * <p>
 * {@code residual = log(elapsed) + log(PF_variant) - log(T_div)}.
 * Positive means the boat was slower than predicted; negative means faster.
 */
public record EntryResidual(
    String raceId,
    String divisionName,
    LocalDate raceDate,
    boolean nonSpinnaker,           // true for NON_SPIN variant
    boolean twoHanded,              // true for TWO_HANDED variant (mutually exclusive with nonSpinnaker)
    double residual,                // log-space residual; positive = slow
    double weight                   // final entry weight after outlier down-weighting
) {}
