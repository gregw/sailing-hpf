package org.mortbay.sailing.pf.analysis;

/**
 * A directed edge in the {@link ConversionGraph}: a fitted linear conversion from one
 * handicap node to another.
 */
public record ConversionEdge(
    ConversionNode from,
    ConversionNode to,
    LinearFit fit
) {}
