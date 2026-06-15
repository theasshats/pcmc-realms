package com.theasshats.pcmcrealms.integration;

import com.theasshats.pcmcrealms.integration.minecolonies.MineColoniesGuardEnforcer;
import com.theasshats.pcmcrealms.integration.minecolonies.MineColoniesMetricsLookup;
import net.neoforged.fml.ModList;

/**
 * Soft-dependency wiring (mirrors pcmc-territory's pattern). The {@code minecolonies} adapter classes
 * reference MineColonies API types directly, but the JVM only links/loads them when the factory
 * methods here instantiate them — gated on {@link ModList#isLoaded}. Absent MineColonies → the
 * {@code NOOP} fallbacks, so SOCIAL laws degrade to a toothless-but-present wanted signal rather than
 * crashing (scope §5).
 */
public final class RealmsIntegrations {

    /** MineColonies' mod id — matches the 1.1.1327-1.21.1 jar, same coordinate pcmc-territory uses. */
    public static final String MINECOLONIES_MOD_ID = "minecolonies";

    private RealmsIntegrations() {}

    public static boolean mineColoniesPresent() {
        return ModList.get().isLoaded(MINECOLONIES_MOD_ID);
    }

    public static GuardEnforcer createGuardEnforcer() {
        return mineColoniesPresent() ? new MineColoniesGuardEnforcer() : GuardEnforcer.NOOP;
    }

    public static MetricsLookup createMetricsLookup() {
        return mineColoniesPresent() ? new MineColoniesMetricsLookup() : MetricsLookup.NOOP;
    }
}
