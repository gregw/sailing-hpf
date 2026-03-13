package org.mortbay.sailing.hpf.data;

import java.util.List;

/**
 * A boat design (hull type).
 * The ID is the normalised design name, e.g. "j24", "farr40".
 * Canonical name is the authoritative display form, preferably from the ORC Class field.
 * Most designs have a single maker, but some (e.g. J/24) have been built by multiple manufacturers.
 */
public record Design(
        String id,            // normalised name, e.g. "j24", "farr40"
        String canonicalName, // display name, e.g. "J/24", "Farr 40"
        List<String> makerIds, // normalised maker IDs; usually one
        List<String> aliases   // alternate design names, e.g. "Mumm 30" for "Farr 30"
) {}
