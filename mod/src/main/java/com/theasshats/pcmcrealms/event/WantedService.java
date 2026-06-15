package com.theasshats.pcmcrealms.event;

import com.theasshats.pcmcrealms.RealmsConfig;
import com.theasshats.pcmcrealms.api.WantedEvent;
import com.theasshats.pcmcrealms.data.GovSavedData;
import com.theasshats.pcmcterritory.api.EntitySnapshot;
import com.theasshats.pcmcterritory.api.TerritoryApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;
import java.util.UUID;

/**
 * Centralizes raising and clearing the wanted signal so the combat handler and the tick purge stay
 * consistent (scope §5). The {@code WantedTable} is the source of truth; this also posts the public
 * {@link WantedEvent} (the canonical signal) and drives the {@link com.theasshats.pcmcrealms.integration.GuardEnforcer}
 * consumer. Server-thread only.
 */
public final class WantedService {

    private WantedService() {}

    /** Marks an offender wanted for the configured window: table + signal + guard flip. */
    public static void mark(ServerLevel level, GovSavedData data, UUID jurisdiction, ServerPlayer offender) {
        markFor(level, data, jurisdiction, offender, RealmsConfig.wantedWindowTicks());
    }

    /** Marks an offender wanted for a specific number of ticks (used by the debug command). */
    public static void markFor(ServerLevel level, GovSavedData data, UUID jurisdiction,
                               ServerPlayer offender, long durationTicks) {
        long expiry = now(level) + durationTicks;
        data.wanted().mark(jurisdiction, offender.getUUID(), expiry);
        data.setDirty();
        NeoForge.EVENT_BUS.post(new WantedEvent.Marked(jurisdiction, offender.getUUID(), expiry));
        data.guardEnforcer().flagHostile(level, offender.getUUID(),
                offender.getGameProfile().getName(), colonyIdsOf(level, jurisdiction));
    }

    /** Pardons a wanted player: removes the table entry, then signals + restores the guard rank. */
    public static void pardon(ServerLevel level, GovSavedData data, UUID jurisdiction, UUID player) {
        data.wanted().clear(jurisdiction, player);
        data.setDirty();
        clear(level, data, jurisdiction, player);
    }

    /** Clears a wanted entry (expiry or pardon): signal + guard restore. The table entry is already gone. */
    public static void clear(ServerLevel level, GovSavedData data, UUID jurisdiction, UUID player) {
        NeoForge.EVENT_BUS.post(new WantedEvent.Cleared(jurisdiction, player));
        data.guardEnforcer().restore(level, player, colonyIdsOf(level, jurisdiction));
    }

    /** Single monotonic clock for all wanted/combat timing: the overworld game time. */
    public static long now(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getGameTime();
    }

    private static Set<Integer> colonyIdsOf(ServerLevel level, UUID jurisdiction) {
        return TerritoryApi.getEntity(level, jurisdiction)
                .map(EntitySnapshot::colonyIds)
                .orElse(Set.of());
    }
}
