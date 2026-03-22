package org.mortbay.sailing.hpf.server;

import org.mortbay.sailing.hpf.analysis.BoatReferenceFactors;
import org.mortbay.sailing.hpf.analysis.ComparisonResult;
import org.mortbay.sailing.hpf.analysis.ConversionGraph;
import org.mortbay.sailing.hpf.analysis.HandicapAnalyser;
import org.mortbay.sailing.hpf.analysis.ReferenceNetworkBuilder;
import org.mortbay.sailing.hpf.store.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Shared cache for analysis results. Holds the output of {@link HandicapAnalyser#analyseAll()}
 * and the reference factor map from {@link ReferenceNetworkBuilder#build(DataStore, int)}.
 * <p>
 * Both are recomputed together via {@link #refresh()} so they are always consistent.
 * {@link #refresh()} is called on startup and after each importer run completes.
 */
public class AnalysisCache
{
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisCache.class);

    private final DataStore store;

    private volatile List<ComparisonResult> comparisons = List.of();
    private volatile Map<String, BoatReferenceFactors> referenceFactors = Map.of();

    public AnalysisCache(DataStore store)
    {
        this.store = store;
    }

    public void refresh()
    {
        LOG.info("AnalysisCache: refreshing...");
        List<ComparisonResult> newComparisons = new HandicapAnalyser(store).analyseAll();

        int currentYear = LocalDate.now().getYear();
        ConversionGraph graph = ConversionGraph.from(newComparisons);
        Map<String, BoatReferenceFactors> newFactors =
            new ReferenceNetworkBuilder().build(store, graph, currentYear);

        comparisons = newComparisons;
        referenceFactors = newFactors;
        LOG.info("AnalysisCache: {} comparisons, {} reference factors", newComparisons.size(), newFactors.size());
    }

    public List<ComparisonResult> comparisons()
    {
        return comparisons;
    }

    public Map<String, BoatReferenceFactors> referenceFactors()
    {
        return referenceFactors;
    }
}
