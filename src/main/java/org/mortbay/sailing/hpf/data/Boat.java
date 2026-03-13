package org.mortbay.sailing.hpf.data;

import java.util.List;

/**
 * A racing boat. Raw layer — immutable, no derived fields, no back-references.
 *
 * The ID is a generated slug: sailnum-firstname-hex, e.g. "aus1234-raging-3f9a".
 * It is stable once assigned and never derived from any source system ID.
 *
 * designId and clubId are nullable: a boat may be of unknown design or without a
 * recorded home club.
 */
public record Boat(
        String id,           // e.g. "aus1234-raging-3f9a"
        String sailNumber,   // normalised sail number, e.g. "aus1234"
        String name,         // canonical name
        String designId,     // normalised design ID, nullable
        String clubId,       // primary home club domain, nullable
        List<String> aliases // alternate names seen for this boat across sources
) {}
