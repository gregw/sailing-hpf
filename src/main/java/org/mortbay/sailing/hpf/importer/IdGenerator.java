package org.mortbay.sailing.hpf.importer;

import java.util.Set;

/**
 * Normalisation and ID slug utilities for importers.
 * All methods are pure functions with no side effects.
 */
public class IdGenerator {

    /**
     * Lowercase, strip all non-alphanumeric characters.
     * "J/24" → "j24", "Farr 40" → "farr40"
     */
    public static String normaliseName(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Same normalisation as normaliseName; separate method for clarity at call sites.
     */
    public static String normaliseSailNumber(String raw) {
        return normaliseName(raw);
    }

    /**
     * Generate a boat ID of the form "{normSail}-{firstWord}", adding a hex suffix
     * only if that base ID already exists in existingIds.
     *
     * Examples:
     *   "AUS1234", "Raging Bull", {} → "aus1234-raging"
     *   "AUS1234", "Raging Bull", {"aus1234-raging"} → "aus1234-raging-0001"
     */
    public static String generateBoatId(String rawSail, String rawName, Set<String> existingIds) {
        String normSail = normaliseSailNumber(rawSail);
        String firstWord = normaliseFirstWord(rawName);
        String base = normSail + "-" + firstWord;
        if (!existingIds.contains(base)) return base;
        for (int i = 1; i <= 0xFFFF; i++) {
            String candidate = base + "-" + String.format("%04x", i);
            if (!existingIds.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot generate unique boat ID for: " + base);
    }

    /**
     * Generate a certificate ID of the form "{boatId}-orc-{year}", adding a
     * zero-padded suffix only on collision.
     *
     * Examples:
     *   "aus1234-raging", 2024, {} → "aus1234-raging-orc-2024"
     *   "aus1234-raging", 2024, {"aus1234-raging-orc-2024"} → "aus1234-raging-orc-2024-001"
     */
    public static String generateCertId(String boatId, int year, Set<String> existingIds) {
        String base = boatId + "-orc-" + year;
        if (!existingIds.contains(base)) return base;
        for (int i = 1; i <= 999; i++) {
            String candidate = base + "-" + String.format("%03d", i);
            if (!existingIds.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot generate unique cert ID for: " + base);
    }

    private static String normaliseFirstWord(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        String[] parts = raw.trim().split("\\s+");
        return normaliseName(parts[0]);
    }

    private IdGenerator() {}
}
