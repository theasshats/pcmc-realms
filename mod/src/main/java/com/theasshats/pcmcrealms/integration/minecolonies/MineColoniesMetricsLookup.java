package com.theasshats.pcmcrealms.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.mojang.logging.LogUtils;
import com.theasshats.pcmcrealms.core.MunicipalityMetrics;
import com.theasshats.pcmcrealms.integration.MetricsLookup;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Set;

/**
 * Reads promotion metrics from MineColonies (scope §8). Aggregates the max across the colonies a
 * jurisdiction binds (a realm with several colonies promotes on its strongest).
 *
 * <p><b>Compile-verified against {@code curse.maven:minecolonies-245506:8186694}, not yet
 * runtime-verified</b> — re-verify on the box / each MineColonies bump (scope §11). All lookups are
 * wrapped so a snapshot API shift degrades to "metric unknown" rather than crashing a command.
 *
 * <p><b>SPIKE — claimed-chunk footprint:</b> MineColonies' public API exposes
 * {@code getLoadedChunkCount()} (currently loaded), not a true claimed-chunk count. We use it as an
 * approximation for the footprint metric; an accurate claimed count needs a better API (or deriving
 * from the colony's claim radius). Tune the {@code promotion.*.minClaimedChunks} thresholds, or set
 * them to 0, until this is firmed up. Tracked on the playtest checklist.
 */
public final class MineColoniesMetricsLookup implements MetricsLookup {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public MunicipalityMetrics metricsFor(ServerLevel level, Set<Integer> colonyIds) {
        int citizens = 0;
        int claimedChunks = 0;
        int townHallLevel = 0;
        for (int colonyId : colonyIds) {
            try {
                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(colonyId, level);
                if (colony == null) {
                    continue;
                }
                citizens = Math.max(citizens, colony.getCitizenManager().getCurrentCitizenCount());
                claimedChunks = Math.max(claimedChunks, colony.getLoadedChunkCount()); // SPIKE: loaded ≈ claimed
                ITownHall townHall = colony.getServerBuildingManager().getTownHall();
                if (townHall != null) {
                    townHallLevel = Math.max(townHallLevel, townHall.getBuildingLevel());
                }
            } catch (Throwable t) {
                LOGGER.warn("[pcmc_realms] metrics lookup failed for colony {} (MineColonies API drift?): {}",
                        colonyId, t.toString());
            }
        }
        return new MunicipalityMetrics(citizens, claimedChunks, townHallLevel);
    }
}
