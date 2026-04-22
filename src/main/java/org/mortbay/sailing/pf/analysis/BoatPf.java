package org.mortbay.sailing.pf.analysis;

import org.mortbay.sailing.pf.data.Factor;

/**
 * Per-boat PF output with three variant values, mirroring {@link ReferenceFactors}.
 * <p>
 * Each variant is a {@link Factor} (value + weight), where weight represents confidence
 * scaled so that 5+ weighted race entries → full confidence. Boats with no races in a
 * variant get their RF as PF (PF == RF) with the RF's own confidence.
 * <p>
 * {@code referenceDelta*} is {@code log(PF) - log(RF)}: positive means boat is slower
 * than its reference factor predicts, negative means faster. Zero if PF == RF (no races).
 */
public record BoatPf(
    Factor spin,                    // nullable if no spin RF either
    Factor nonSpin,                 // nullable if no nonSpin RF either
    Factor twoHanded,               // nullable if no twoHanded RF either
    double referenceDeltaSpin,      // log(pf) - log(rf); 0.0 if no spin races
    double referenceDeltaNonSpin,
    double referenceDeltaTwoHanded,
    int spinRaceCount,              // number of division entries contributing to spin PF
    int nonSpinRaceCount,
    int twoHandedRaceCount
) {}
