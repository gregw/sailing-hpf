package org.mortbay.sailing.hpf.analysis;

/**
 * Per-division HPF output: the reference time and quality metrics for one division
 * (the race unit for HPF optimisation — one T₀ per division).
 */
public record DivisionHpf(
    String divisionName,
    double referenceTimeNanos,      // T₀ in nanoseconds (time a 1.000 boat would take)
    double dispersion,              // weighted IQR / T₀ ratio; lower = more consistent
    double weight                   // final division weight used in optimisation
) {}
