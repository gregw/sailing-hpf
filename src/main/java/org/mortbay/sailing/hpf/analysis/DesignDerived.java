package org.mortbay.sailing.hpf.analysis;

import org.mortbay.sailing.hpf.data.Design;

import java.util.Set;

/**
 * Consolidated per-design derived data: reference factors and navigation indexes.
 * Held in {@link org.mortbay.sailing.hpf.server.AnalysisCache}, never serialized.
 */
public record DesignDerived(
    Design design,
    ReferenceFactors referenceFactors,  // nullable; generations = ceil(avg of contributing boats)
    Set<String> boatIds
) {}
