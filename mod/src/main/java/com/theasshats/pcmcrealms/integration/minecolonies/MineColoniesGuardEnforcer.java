package com.theasshats.pcmcrealms.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.logging.LogUtils;
import com.theasshats.pcmcrealms.integration.GuardEnforcer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The MineColonies guard rank-flip (scope §5). On a SOCIAL violation, sets the offender to the
 * colony's Fight-Guards (Hostile) rank so its guards aggro natively; restores the prior rank when
 * the wanted window ends. "No guards nearby → get away with it" falls out for free (a Hostile rank
 * with no guards in range simply does nothing).
 *
 * <p><b>API (confirmed present in the MineColonies fork source), runtime behavior (SPIKE — do this
 * before trusting it):</b> {@code IPermissions#setPlayerRank(UUID, Rank, Level)},
 * {@code getRankHostile()}, {@code getRank(UUID)}, {@code addPlayer}/{@code removePlayer} are all
 * public API. What a real instance must confirm (scope §5 spike, on the playtest checklist):
 * (1) setting the Hostile rank actually makes guards attack; (2) restore puts the prior rank back;
 * (3) wanted state survives a restart without stranding a player as Hostile. Every call is wrapped
 * so an API drift degrades to "no guard teeth" (the wanted signal still stands) rather than crashing.
 *
 * <p><b>Prior-rank store is in-memory (SPIKE limitation):</b> within a session, restore returns the
 * exact prior rank. Across a restart the map is lost, so an expiring entry restores to the colony's
 * Neutral rank (a member's officer rank would not survive). The canonical truth is the persisted
 * {@code WantedTable}; re-deriving the flip on load and persisting prior ranks is a follow-up the
 * spike informs. The decoupled wanted signal is unaffected.
 */
public final class MineColoniesGuardEnforcer implements GuardEnforcer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** key {@code colonyId:player} → prior rank id, or {@link #ADDED} if we added a non-member. */
    private static final int ADDED = -1;
    private final Map<String, Integer> priorRank = new ConcurrentHashMap<>();

    @Override
    public void flagHostile(ServerLevel level, UUID player, String playerName, Set<Integer> colonyIds) {
        for (int colonyId : colonyIds) {
            try {
                IColony colony = colony(level, colonyId);
                if (colony == null) {
                    continue;
                }
                IPermissions perms = colony.getPermissions();
                Rank hostile = perms.getRankHostile();
                if (hostile == null) {
                    continue;
                }
                String key = key(colonyId, player);
                boolean inColony = perms.getPlayers().containsKey(player);
                if (inColony) {
                    priorRank.put(key, perms.getRank(player).getId());
                    perms.setPlayerRank(player, hostile, level);
                } else {
                    priorRank.put(key, ADDED);
                    perms.addPlayer(player, playerName, hostile);
                }
            } catch (Throwable t) {
                LOGGER.warn("[pcmc_realms] guard flag failed for player {} in colony {} (MineColonies API drift?): {}",
                        player, colonyId, t.toString());
            }
        }
    }

    @Override
    public void restore(ServerLevel level, UUID player, Set<Integer> colonyIds) {
        for (int colonyId : colonyIds) {
            try {
                IColony colony = colony(level, colonyId);
                if (colony == null) {
                    continue;
                }
                IPermissions perms = colony.getPermissions();
                Integer prior = priorRank.remove(key(colonyId, player));
                if (prior != null && prior == ADDED) {
                    perms.removePlayer(player);
                } else if (prior != null) {
                    Rank priorRankObj = perms.getRank((int) prior);
                    if (priorRankObj != null) {
                        perms.setPlayerRank(player, priorRankObj, level);
                    }
                } else {
                    // Unknown prior (e.g. after a restart): fall back to Neutral so the player isn't
                    // stranded Hostile. Documented SPIKE limitation above.
                    Rank neutral = perms.getRankNeutral();
                    if (neutral != null && perms.getPlayers().containsKey(player)) {
                        perms.setPlayerRank(player, neutral, level);
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("[pcmc_realms] guard restore failed for player {} in colony {} (MineColonies API drift?): {}",
                        player, colonyId, t.toString());
            }
        }
    }

    /**
     * Resolves a colony by id, robust to dimension: tries the given level first, then scans the
     * server's other dimensions. This matters for restore — the wanted window can expire while the
     * offender is in a different dimension than the colony, and stranding them Hostile is the failure
     * mode the spike guards against.
     */
    private static IColony colony(ServerLevel level, int colonyId) {
        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(colonyId, level);
        if (colony != null) {
            return colony;
        }
        for (net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key : level.getServer().levelKeys()) {
            colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByDimension(colonyId, key);
            if (colony != null) {
                return colony;
            }
        }
        return null;
    }

    private static String key(int colonyId, UUID player) {
        return colonyId + ":" + player;
    }
}
