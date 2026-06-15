package com.theasshats.pcmcrealms;

import com.theasshats.pcmcrealms.core.PromotionThresholds;
import com.theasshats.pcmcrealms.core.Tier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server-side config (scope §5, §6, §8): the SOCIAL combat/wanted windows and the municipality
 * promotion thresholds. Server config because all of it is server-authoritative and these are
 * balance knobs an operator tunes per world. Ticks are 20/second.
 */
public final class RealmsConfig {

    public static final ModConfigSpec SPEC;

    // --- Enforcement (§5) ---
    private static final ModConfigSpec.IntValue COMBAT_WINDOW_TICKS;
    private static final ModConfigSpec.IntValue WANTED_WINDOW_TICKS;
    private static final ModConfigSpec.IntValue PURGE_INTERVAL_TICKS;

    // --- Promotion thresholds (§8): minimums to reach each municipality tier ---
    private static final ModConfigSpec.IntValue VILLAGE_CITIZENS;
    private static final ModConfigSpec.IntValue VILLAGE_CHUNKS;
    private static final ModConfigSpec.IntValue VILLAGE_TOWNHALL;
    private static final ModConfigSpec.IntValue TOWN_CITIZENS;
    private static final ModConfigSpec.IntValue TOWN_CHUNKS;
    private static final ModConfigSpec.IntValue TOWN_TOWNHALL;
    private static final ModConfigSpec.IntValue CITY_CITIZENS;
    private static final ModConfigSpec.IntValue CITY_CHUNKS;
    private static final ModConfigSpec.IntValue CITY_TOWNHALL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Soft-law (SOCIAL) enforcement — see scope §5").push("enforcement");
        COMBAT_WINDOW_TICKS = b
                .comment("Self-defense window: a hit within this many ticks after being struck is retaliation, not a crime (default 300 = 15s)")
                .defineInRange("combatWindowTicks", 300, 1, 12_000);
        WANTED_WINDOW_TICKS = b
                .comment("How long a 'wanted' status lasts after an unjustified violation (default 1200 = 60s)")
                .defineInRange("wantedWindowTicks", 1200, 20, 1_728_000);
        PURGE_INTERVAL_TICKS = b
                .comment("How often expired wanted/aggression records are swept (default 100 = 5s)")
                .defineInRange("purgeIntervalTicks", 100, 20, 12_000);
        b.pop();

        b.comment("Municipality promotion thresholds — minimums to reach each tier (scope §8)").push("promotion");
        b.push("village");
        VILLAGE_CITIZENS = b.defineInRange("minCitizens", 4, 0, 100_000);
        VILLAGE_CHUNKS = b.defineInRange("minClaimedChunks", 4, 0, 1_000_000);
        VILLAGE_TOWNHALL = b.defineInRange("minTownHallLevel", 1, 0, 5);
        b.pop();
        b.push("town");
        TOWN_CITIZENS = b.defineInRange("minCitizens", 10, 0, 100_000);
        TOWN_CHUNKS = b.defineInRange("minClaimedChunks", 16, 0, 1_000_000);
        TOWN_TOWNHALL = b.defineInRange("minTownHallLevel", 3, 0, 5);
        b.pop();
        b.push("city");
        CITY_CITIZENS = b.defineInRange("minCitizens", 20, 0, 100_000);
        CITY_CHUNKS = b.defineInRange("minClaimedChunks", 40, 0, 1_000_000);
        CITY_TOWNHALL = b.defineInRange("minTownHallLevel", 5, 0, 5);
        b.pop();
        b.pop();

        SPEC = b.build();
    }

    private RealmsConfig() {}

    public static int combatWindowTicks() {
        return COMBAT_WINDOW_TICKS.get();
    }

    public static int wantedWindowTicks() {
        return WANTED_WINDOW_TICKS.get();
    }

    public static int purgeIntervalTicks() {
        return PURGE_INTERVAL_TICKS.get();
    }

    /** Builds the engine threshold model from the current config values. */
    public static PromotionThresholds promotionThresholds() {
        Map<Tier, PromotionThresholds.Requirement> m = new EnumMap<>(Tier.class);
        m.put(Tier.VILLAGE, new PromotionThresholds.Requirement(
                VILLAGE_CITIZENS.get(), VILLAGE_CHUNKS.get(), VILLAGE_TOWNHALL.get()));
        m.put(Tier.TOWN, new PromotionThresholds.Requirement(
                TOWN_CITIZENS.get(), TOWN_CHUNKS.get(), TOWN_TOWNHALL.get()));
        m.put(Tier.CITY, new PromotionThresholds.Requirement(
                CITY_CITIZENS.get(), CITY_CHUNKS.get(), CITY_TOWNHALL.get()));
        return PromotionThresholds.of(m);
    }
}
