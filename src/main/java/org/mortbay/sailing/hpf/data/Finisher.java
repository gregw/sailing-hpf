package org.mortbay.sailing.hpf.data;

import java.time.Duration;

/**
 * A boat's finishing result within a division. Raw layer — immutable, no derived fields.
 * Only finishers are imported; elapsedTime is always present.
 * Uniquely identified by boatId within its containing Division.
 */
public record Finisher(
    String boatId,
    Duration elapsedTime,
    boolean nonSpinnaker
) {}
