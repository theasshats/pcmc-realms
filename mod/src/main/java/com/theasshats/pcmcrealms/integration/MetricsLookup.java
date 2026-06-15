package com.theasshats.pcmcrealms.integration;

import com.theasshats.pcmcrealms.core.MunicipalityMetrics;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Reads the free MineColonies metrics promotion validates against (scope §8): population, footprint,
 * development. {@link #NOOP} (all zeros) when MineColonies is absent — a colony-less realm can't
 * promote on these metrics, which is correct.
 */
public interface MetricsLookup {

    /** Aggregated metrics across the colonies bound to a jurisdiction (max across colonies). */
    MunicipalityMetrics metricsFor(ServerLevel level, Set<Integer> colonyIds);

    MetricsLookup NOOP = (level, colonyIds) -> new MunicipalityMetrics(0, 0, 0);
}
