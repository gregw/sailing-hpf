package org.mortbay.sailing.hpf.data;

/**
 * Handicap systems observed in race data.
 * Only IRC, ORC and AMS are measurement-based and usable as reference anchors.
 * PHS and CBH values are excluded from analysis; elapsed times from those races are valid.
 */
public enum HandicapSystem
{
    IRC,
    ORC,
    AMS,
    PHS,
    CBH,
    UNKNOWN
}
