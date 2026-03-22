package org.mortbay.sailing.hpf.analysis;

import java.util.List;

/**
 * The result of a handicap comparison regression.
 * <p>
 * {@code pairs} contains the raw (x, y) observations in TCF space.
 * {@code fit} is the OLS result; it may be {@code null} if there are fewer than 3 pairs.
 */
public record ComparisonResult(
    ComparisonKey key,
    List<DataPair> pairs,
    LinearFit fit   // null when pairs.size() < 3
)
{
    /** Convenience: number of data pairs. */
    public int n()
    {
        return pairs.size();
    }
}
