package org.mortbay.sailing.pf.analysis;

/**
 * A single paired observation used for handicap conversion regression.
 * Both {@code x} and {@code y} are in TCF (time correction factor) space.
 */
public record DataPair(String boatId, double x, double y) {}
