package org.mortbay.sailing.hpf.data;

import java.util.List;

/**
 * A sailing club that organises races.
 * The ID is the club's website domain (e.g. "myc.com.au"), which is globally unique
 * and independent of any source system.
 */
public record Club(
        String id,           // website domain, e.g. "myc.com.au"
        String shortName,    // e.g. "MYC"
        String longName,     // e.g. "Manly Yacht Club"
        List<String> aliases // alternate short names or former domains
) {}
