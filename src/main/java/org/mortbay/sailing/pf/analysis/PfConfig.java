package org.mortbay.sailing.pf.analysis;

/**
 * Immutable parameters for the PF optimiser.
 */
public record PfConfig(
    double lambda,                  // regularisation strength (pulls PF toward RF)
    double convergenceThreshold,    // max |Δlog(PF)| per inner iteration for convergence
    int maxInnerIterations,         // ALS convergence limit
    int maxOuterIterations,         // reweighting cycles
    double outlierK,                // IQR multiplier for entry down-weighting (Cauchy scale)
    double asymmetryFactor,         // extra penalty for fast outliers (residual < 0)
    double outerDampingFactor,      // blend fraction for outer weight updates (1.0=no damping, 0.5=half step)
    double outerConvergenceThreshold, // max weight change to declare outer convergence (default 0.01)
    double crossVariantLambda       // couples variant PFs via RF ratio; 0 = disabled
)
{
    public static final PfConfig DEFAULT = new PfConfig(1.0, 0.0001, 100, 5, 2.0, 2.0, 0.5, 0.01, 0.0);
}
