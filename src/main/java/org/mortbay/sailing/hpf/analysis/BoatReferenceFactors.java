package org.mortbay.sailing.hpf.analysis;

import org.mortbay.sailing.hpf.data.Factor;

/**
 * First-order reference factors for a single boat, one per racing variant.
 * Each factor is the aggregated IRC-equivalent TCF for the current year,
 * derived from all available certificates via empirical conversion tables.
 * <p>
 * A null factor means no valid conversion path was found for that variant.
 */
public record BoatReferenceFactors(
    Factor spin,       // IRC spin equivalent for currentYear; null if no path found
    Factor nonSpin,    // IRC non-spinnaker equivalent; null if no path found
    Factor twoHanded   // IRC two-handed equivalent; null if no path found
) {}
