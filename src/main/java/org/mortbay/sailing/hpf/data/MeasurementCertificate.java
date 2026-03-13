package org.mortbay.sailing.hpf.data;

import java.time.LocalDate;

/**
 * A measurement-based handicap certificate held by a boat.
 * Only IRC, ORC and AMS certificates are stored — PHS and CBH are excluded.
 *
 * The ID follows the pattern: boatId-system-year-seq, e.g. "aus1234-raging-3f9a-irc-2024-001".
 *
 * Value semantics by system:
 *   IRC — TCC (time correction factor), e.g. 0.987
 *   ORC — GPH (general purpose handicap, seconds/mile), e.g. 588.4; convert to TCF via 600/GPH
 *   AMS — AMS value, same scale as IRC TCC
 *
 * certificateNumber and expiryDate are null for AMS (no physical certificate document).
 */
public record MeasurementCertificate(
        String id,               // e.g. "aus1234-raging-3f9a-irc-2024-001"
        String boatId,
        HandicapSystem system,   // IRC, ORC, or AMS
        int year,                // certificate year
        double value,            // TCC for IRC/AMS, GPH for ORC
        boolean nonSpinnaker,    // true if this is a non-spinnaker certificate
        String certificateNumber, // null for AMS; dxtID for ORC
        LocalDate expiryDate     // null for AMS
) {}
