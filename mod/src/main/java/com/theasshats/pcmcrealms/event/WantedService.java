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

    /** Marks an offender wanted in a jurisdiction: table + signal + guard flip. */
    public static void mark(ServerLevel level, GovSavedData data, UUID jurisdiction, ServerPlayer offender) {
        long expiry = now(level) + RealmsConfig.wantedWindowTicks();
        data.wanted().mark(jurisdiction, offender.getUUID(), expiry);
        data.setDirty();
        NeoForge.EVENT_BUS.post(new WantedEvent.Marked(jurisdiction, offender.getUUID(), expiry));
        data.guardEnforcer().flagHostile(level, offender.getUUID(),
                offender.getGameProfile().getName(), colonyIdsOf(level, jurisdiction));
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
