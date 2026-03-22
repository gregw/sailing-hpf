package org.mortbay.sailing.hpf.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mortbay.sailing.hpf.data.Boat;
import org.mortbay.sailing.hpf.data.Certificate;
import org.mortbay.sailing.hpf.data.Factor;
import org.mortbay.sailing.hpf.store.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a map of boatId → {@link BoatReferenceFactors} by converting each boat's
 * measurement certificates (IRC/ORC/AMS, any recent year) to current-year IRC equivalents
 * via the empirical conversion tables in {@link ConversionGraph}.
 *
 * <h2>Algorithm</h2>
 * For each boat and each target variant (spin, non-spin, two-handed):
 * <ol>
 *   <li>Primary pass: enumerate all conversion paths using <em>same-variant</em> edges only
 *       (no cross-variant conversions like NS→spin).</li>
 *   <li>Fallback pass: if the primary pass found no factors, allow cross-variant edges so
 *       a boat with only spin certs can still produce an NS estimate (and vice-versa).</li>
 *   <li>All factors from all paths across all certs are combined with
 *       {@link Factor#aggregate}. Paths that agree reinforce confidence; paths that
 *       disagree are penalised by the variance term.</li>
 * </ol>
 *
 * <h2>Base weights</h2>
 * <ul>
 *   <li>IRC: 1.0</li>
 *   <li>ORC international: 1.0</li>
 *   <li>ORC club ({@link Certificate#orcClub()} = true): 0.8</li>
 *   <li>AMS: 1.0</li>
 * </ul>
 *
 * <h2>Age cap</h2>
 * Certificates older than {@link #MAX_CERT_AGE_YEARS} years are ignored.
 */
public class ReferenceNetworkBuilder
{
    private static final Logger LOG = LoggerFactory.getLogger(ReferenceNetworkBuilder.class);

    /** Certificates older than this many years relative to currentYear are ignored. */
    public static final int MAX_CERT_AGE_YEARS = 5;

    /** Base weight for ORC club certificates (vs 1.0 for international). */
    public static final double ORC_CLUB_BASE_WEIGHT = 0.8;

    /** DFS paths with accumulated weight below this threshold are pruned. */
    private static final double MIN_PATH_WEIGHT = 0.001;

    /** Maximum DFS depth (number of conversion hops) to prevent runaway traversal. */
    private static final int MAX_DEPTH = 8;

    /**
     * Computes reference factors for a single boat using a pre-built conversion graph.
     * Use this when the graph has already been built to avoid re-running the analyser.
     */
    public BoatReferenceFactors buildForBoat(Boat boat, ConversionGraph graph, int currentYear)
    {
        List<Certificate> validCerts = validCerts(boat, currentYear);

        Factor spin    = computeVariantFactor(validCerts, graph, currentYear, false, false, false);
        Factor nonSpin = computeVariantFactor(validCerts, graph, currentYear, true,  false, false);
        Factor twoH    = computeVariantFactor(validCerts, graph, currentYear, false, true,  false);

        if (spin    == null) spin    = computeVariantFactor(validCerts, graph, currentYear, false, false, true);
        if (nonSpin == null) nonSpin = computeVariantFactor(validCerts, graph, currentYear, true,  false, true);
        if (twoH    == null) twoH    = computeVariantFactor(validCerts, graph, currentYear, false, true,  true);

        return new BoatReferenceFactors(spin, nonSpin, twoH);
    }

    /**
     * Builds a ConversionGraph from the store's current analysis results.
     */
    public ConversionGraph buildGraph(DataStore store)
    {
        return ConversionGraph.from(new HandicapAnalyser(store).analyseAll());
    }

    /**
     * Builds the reference factor map for all boats in the store.
     *
     * @param store       the data store containing boats and their certificates
     * @param currentYear the year to which all certificates are converted (e.g. 2025)
     * @return map from boatId to BoatReferenceFactors; every boat gets an entry,
     *         with null Factor fields where no conversion path was found
     */
    public Map<String, BoatReferenceFactors> build(DataStore store, int currentYear)
    {
        ConversionGraph graph = buildGraph(store);
        return build(store, graph, currentYear);
    }

    /**
     * Builds the reference factor map using a pre-built conversion graph.
     * Use this when the graph has already been built (e.g. from a shared cache)
     * to avoid re-running {@link HandicapAnalyser#analyseAll()}.
     */
    public Map<String, BoatReferenceFactors> build(DataStore store, ConversionGraph graph, int currentYear)
    {

        Map<String, BoatReferenceFactors> result = new LinkedHashMap<>();

        for (Boat boat : store.boats().values())
        {
            List<Certificate> validCerts = validCerts(boat, currentYear);

            Factor spin    = computeVariantFactor(validCerts, graph, currentYear, false, false, false);
            Factor nonSpin = computeVariantFactor(validCerts, graph, currentYear, true,  false, false);
            Factor twoH    = computeVariantFactor(validCerts, graph, currentYear, false, true,  false);

            // Fallback: allow cross-variant if primary pass found nothing
            if (spin    == null) spin    = computeVariantFactor(validCerts, graph, currentYear, false, false, true);
            if (nonSpin == null) nonSpin = computeVariantFactor(validCerts, graph, currentYear, true,  false, true);
            if (twoH    == null) twoH    = computeVariantFactor(validCerts, graph, currentYear, false, true,  true);

            result.put(boat.id(), new BoatReferenceFactors(spin, nonSpin, twoH));

            if (spin != null)
                LOG.debug("boat={} spin=({}, w={})", boat.id(),
                    String.format("%.4f", spin.value()), String.format("%.3f", spin.weight()));
        }

        long withSpin = result.values().stream().filter(r -> r.spin() != null).count();
        LOG.info("ReferenceNetworkBuilder: {} boats, {} with spin factor", result.size(), withSpin);
        return result;
    }

    /**
     * Filters a boat's certificates to those within the age cap and with plausible values.
     */
    private static List<Certificate> validCerts(Boat boat, int currentYear)
    {
        List<Certificate> valid = new ArrayList<>();
        for (Certificate c : boat.certificates())
        {
            if (c.year() < currentYear - MAX_CERT_AGE_YEARS)
                continue;
            if (c.value() < 0.3 || c.value() > 3.0)
                continue;
            valid.add(c);
        }
        return valid;
    }

    /**
     * Computes the aggregated reference factor for one variant by finding all conversion
     * paths from the boat's certificates to the target IRC node for the given variant.
     *
     * @param nonSpinTarget  target is IRC non-spinnaker
     * @param twoHandedTarget target is IRC two-handed
     * @param allowCrossVariant if true, cross-variant edges (e.g. NS→spin) are included
     * @return aggregated Factor, or null if no valid paths were found
     */
    private static Factor computeVariantFactor(List<Certificate> certs,
                                               ConversionGraph graph,
                                               int currentYear,
                                               boolean nonSpinTarget,
                                               boolean twoHandedTarget,
                                               boolean allowCrossVariant)
    {
        ConversionNode target = new ConversionNode("IRC", currentYear, nonSpinTarget, twoHandedTarget);

        List<Factor> allFactors = new ArrayList<>();
        for (Certificate cert : certs)
        {
            ConversionNode start = new ConversionNode(
                cert.system(), cert.year(), cert.nonSpinnaker(), cert.twoHanded());
            double baseWeight = cert.orcClub() ? ORC_CLUB_BASE_WEIGHT : 1.0;

            dfsAllPaths(start, cert.value(), baseWeight, target,
                graph, allowCrossVariant, new HashSet<>(), 0, allFactors);
        }

        if (allFactors.isEmpty())
            return null;
        return Factor.aggregate(allFactors.toArray(new Factor[0]));
    }

    /**
     * Depth-first search enumerating all conversion paths from {@code node} to {@code target}.
     * Each complete path produces one {@link Factor} added to {@code results}.
     *
     * @param node             current graph node
     * @param value            current predicted TCF value at this node
     * @param weight           accumulated path weight so far
     * @param target           the destination node
     * @param graph            the conversion graph
     * @param allowCrossVariant include cross-variant edges in traversal
     * @param visited          nodes visited on the current path (for cycle prevention)
     * @param depth            current recursion depth
     * @param results          accumulator for completed path factors
     */
    private static void dfsAllPaths(ConversionNode node,
                                    double value,
                                    double weight,
                                    ConversionNode target,
                                    ConversionGraph graph,
                                    boolean allowCrossVariant,
                                    Set<ConversionNode> visited,
                                    int depth,
                                    List<Factor> results)
    {
        if (node.equals(target))
        {
            results.add(new Factor(value, Math.min(weight, 1.0)));
            return;
        }

        if (depth >= MAX_DEPTH || weight < MIN_PATH_WEIGHT)
            return;

        visited.add(node);

        List<ConversionEdge> edges = allowCrossVariant
            ? graph.adjacencies(node)
            : graph.sameVariantAdjacencies(node);

        for (ConversionEdge edge : edges)
        {
            if (visited.contains(edge.to()))
                continue;

            double nextValue  = edge.fit().predict(value);
            if (nextValue <= 0)
                continue;
            double nextWeight = weight * edge.fit().weight(value);

            dfsAllPaths(edge.to(), nextValue, nextWeight, target,
                graph, allowCrossVariant, visited, depth + 1, results);
        }

        visited.remove(node);
    }
}
