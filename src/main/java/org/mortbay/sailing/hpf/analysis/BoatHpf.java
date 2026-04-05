package org.mortbay.sailing.hpf.analysis;

import org.mortbay.sailing.hpf.data.Factor;

/**
 * Per-boat HPF output with three variant values, mirroring {@link ReferenceFactors}.
 * <p>
 * Each variant is a {@link Factor} (value + weight), where weight represents confidence
 * scaled so that 5+ weighted race entries → full confidence. Boats with no races in a
 * variant get their RF as HPF (HPF == RF) with the RF's own confidence.
 * <p>
 * {@code referenceDelta*} is {@code log(HPF) - log(RF)}: positive means boat is slower
 * than its reference factor predicts, negative means faster. Zero if HPF == RF (no races).
 */
public record BoatHpf(
    Factor spin,                    // nullable if no spin RF either
    Factor nonSpin,                 // nullable if no nonSpin RF either
    Factor twoHanded,               // nullable if no twoHanded RF either
    double referenceDeltaSpin,      // log(hpf) - log(rf); 0.0 if no spin races
    double referenceDeltaNonSpin,
    double referenceDeltaTwoHanded,
    int spinRaceCount,              // number of division entries contributing to spin HPF
    int nonSpinRaceCount,
    int twoHandedRaceCount
) {}
