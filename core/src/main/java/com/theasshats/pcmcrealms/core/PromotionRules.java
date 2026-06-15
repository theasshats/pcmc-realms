package com.theasshats.pcmcrealms.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates a municipality promotion attempt (scope §8): the entity must be a municipality below
 * {@link Tier#CITY}, and its free MineColonies {@link MunicipalityMetrics} must meet the
 * {@link PromotionThresholds} for the next tier. Pure — validated on the command, never per-tick.
 *
 * <p>Denial reasons are stable codes (the {@code REASON_*} constants) so {@code :mod} can map them
 * to translation keys without parsing prose.
 */
public final class PromotionRules {

    public static final String REASON_NOT_MUNICIPALITY = "not_municipality";
    public static final String REASON_ALREADY_MAX_MUNICIPALITY = "already_max_municipality";
    public static final String REASON_NO_THRESHOLD_CONFIGURED = "no_threshold_configured";
    public static final String REASON_CITIZENS = "citizens";
    public static final String REASON_CLAIMED_CHUNKS = "claimed_chunks";
    public static final String REASON_TOWN_HALL_LEVEL = "town_hall_level";

    private PromotionRules() {}

    public static PromotionResult validate(Tier current, MunicipalityMetrics metrics,
                                           PromotionThresholds thresholds) {
        if (current.isFederation()) {
            // Federations are composed and carved, not promoted (scope §8).
            return PromotionResult.denied(Optional.empty(), List.of(REASON_NOT_MUNICIPALITY));
        }
        Optional<Tier> next = current.next();
        if (next.isEmpty()) {
            // At CITY — the top of the municipality ladder; beyond it is a federation (compose one).
            return PromotionResult.denied(Optional.empty(), List.of(REASON_ALREADY_MAX_MUNICIPALITY));
        }
        Tier target = next.get();
        Optional<PromotionThresholds.Requirement> req = thresholds.forTier(target);
        if (req.isEmpty()) {
            return PromotionResult.denied(Optional.of(target), List.of(REASON_NO_THRESHOLD_CONFIGURED));
        }

        List<String> unmet = new ArrayList<>();
        PromotionThresholds.Requirement r = req.get();
        if (metrics.citizens() < r.minCitizens()) unmet.add(REASON_CITIZENS);
        if (metrics.claimedChunks() < r.minClaimedChunks()) unmet.add(REASON_CLAIMED_CHUNKS);
        if (metrics.townHallLevel() < r.minTownHallLevel()) unmet.add(REASON_TOWN_HALL_LEVEL);

        return unmet.isEmpty()
                ? PromotionResult.allowed(target)
                : PromotionResult.denied(Optional.of(target), unmet);
    }
}
