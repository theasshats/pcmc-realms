package com.theasshats.pcmcrealms.core;

import java.util.Optional;

/**
 * The governance tier ladder (scope §8). Municipality tiers are reached by
 * {@link #next() promotion} (player-initiated, validated on the command against free
 * MineColonies metrics — see {@link PromotionRules}); federation tiers are reached by
 * <em>composition</em> ({@code /realm federate}) and {@code carve}, not promotion.
 *
 * <p>{@link #rank()} is the precedence weight the law resolver uses: a higher rank's law
 * overrides a lower one (scope §4). Ranks are dense 0..6 so the ordinal doubles as the rank.
 */
public enum Tier {
    // Municipality kind — promoted up the ladder.
    SETTLEMENT(0, Kind.MUNICIPALITY),
    VILLAGE(1, Kind.MUNICIPALITY),
    TOWN(2, Kind.MUNICIPALITY),
    CITY(3, Kind.MUNICIPALITY),
    // Federation kind — composed, then carved.
    COUNTY(4, Kind.FEDERATION),
    KINGDOM(5, Kind.FEDERATION),
    EMPIRE(6, Kind.FEDERATION);

    /** Whether a tier is a municipality (a place) or a federation (a polity of places). */
    public enum Kind { MUNICIPALITY, FEDERATION }

    private final int rank;
    private final Kind kind;

    Tier(int rank, Kind kind) {
        this.rank = rank;
        this.kind = kind;
    }

    /** Precedence weight for law resolution; higher wins (scope §4). */
    public int rank() {
        return rank;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isMunicipality() {
        return kind == Kind.MUNICIPALITY;
    }

    public boolean isFederation() {
        return kind == Kind.FEDERATION;
    }

    /**
     * The next municipality tier up, if one exists. Empty at {@link #CITY} (the top of the
     * municipality ladder — beyond it is federation territory, reached by composition not
     * promotion) and for every federation tier. Promotion (§8) only ever climbs this.
     */
    public Optional<Tier> next() {
        if (this == SETTLEMENT) return Optional.of(VILLAGE);
        if (this == VILLAGE) return Optional.of(TOWN);
        if (this == TOWN) return Optional.of(CITY);
        return Optional.empty();
    }
}
