package com.theasshats.pcmcrealms.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The per-target-tier requirements for municipality promotion (scope §8). Thresholds live in
 * {@code realms-server.toml}; {@code :mod} loads them into one of these. Only municipality targets
 * above {@link Tier#SETTLEMENT} (VILLAGE, TOWN, CITY) are reachable by promotion — federation tiers
 * are composed, not promoted.
 */
public final class PromotionThresholds {

    /** The minimums to reach a given tier. */
    public record Requirement(int minCitizens, int minClaimedChunks, int minTownHallLevel) {}

    private final Map<Tier, Requirement> byTier;

    private PromotionThresholds(Map<Tier, Requirement> byTier) {
        this.byTier = new EnumMap<>(byTier);
    }

    public static PromotionThresholds of(Map<Tier, Requirement> byTier) {
        return new PromotionThresholds(byTier);
    }

    /**
     * The placeholder defaults used until {@code realms-server.toml} overrides them. Numbers are a
     * gentle ramp; balance is a playtest/config concern, not engine logic.
     */
    public static PromotionThresholds defaults() {
        Map<Tier, Requirement> m = new EnumMap<>(Tier.class);
        m.put(Tier.VILLAGE, new Requirement(4, 4, 1));
        m.put(Tier.TOWN, new Requirement(10, 16, 3));
        m.put(Tier.CITY, new Requirement(20, 40, 5));
        return new PromotionThresholds(m);
    }

    /** The requirement to reach {@code target}, or empty if {@code target} isn't promotion-reachable. */
    public java.util.Optional<Requirement> forTier(Tier target) {
        return java.util.Optional.ofNullable(byTier.get(Objects.requireNonNull(target, "target")));
    }
}
