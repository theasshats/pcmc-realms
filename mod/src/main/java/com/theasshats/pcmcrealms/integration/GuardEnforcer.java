package com.theasshats.pcmcrealms.integration;

import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;

/**
 * Consumer of the wanted signal that gives SOCIAL law its NPC teeth (scope §5): flips an offender
 * to a Fight-Guards (Hostile) MineColonies rank for the wanted window, then restores. This is
 * <em>one</em> consumer of the {@link com.theasshats.pcmcrealms.core.WantedTable} — the table is the
 * source of truth, so the model survives the guard API and feeds bounties/killfeed later.
 *
 * <p>{@link #NOOP} when MineColonies is absent: the wanted signal still fires (and OPAC-only /
 * ship jurisdictions are player/bounty-enforced, which is on-theme), there are simply no guards.
 */
public interface GuardEnforcer {

    /** Flags {@code player} hostile in every colony bound to the jurisdiction, so its guards aggro. */
    void flagHostile(ServerLevel level, UUID player, String playerName, Set<Integer> colonyIds);

    /** Restores {@code player}'s prior rank in those colonies when the wanted window ends. */
    void restore(ServerLevel level, UUID player, Set<Integer> colonyIds);

    GuardEnforcer NOOP = new GuardEnforcer() {
        @Override
        public void flagHostile(ServerLevel level, UUID player, String playerName, Set<Integer> colonyIds) {
        }

        @Override
        public void restore(ServerLevel level, UUID player, Set<Integer> colonyIds) {
        }
    };
}
