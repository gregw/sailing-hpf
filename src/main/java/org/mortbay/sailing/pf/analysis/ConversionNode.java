package org.mortbay.sailing.pf.analysis;

/**
 * A node in the {@link ConversionGraph}: identifies a position in handicap conversion space.
 * e.g. (ORC, 2023, nonSpinnaker=false, twoHanded=false) = ORC spin 2023.
 */
public record ConversionNode(
    String system,
    int year,
    boolean nonSpinnaker,
    boolean twoHanded
) {}
