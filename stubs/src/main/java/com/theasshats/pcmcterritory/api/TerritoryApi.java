package com.theasshats.pcmcterritory.api;

import com.theasshats.pcmcterritory.core.TerritoryChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * STUB mirror of pcmc-territory's {@code api.TerritoryApi} (see stubs/build.gradle).
 * Signatures read verbatim from Part 1's PR branch; bodies throw because the real
 * pcmc_territory mod provides the implementation at runtime.
 */
public final class TerritoryApi {

    private TerritoryApi() {}

    /** Governing chain for {@code pos}, leaf first (length 0 = wilderness, 1 = the leaf). */
    public static List<UUID> resolve(ServerLevel level, ChunkPos pos) {
        throw stub();
    }

    public static Optional<EntitySnapshot> getEntity(ServerLevel level, UUID entityId) {
        throw stub();
    }

    public static Optional<EntitySnapshot> getEntityByName(ServerLevel level, String name) {
        throw stub();
    }

    public static TerritoryChunk toTerritoryChunk(ServerLevel level, ChunkPos pos) {
        throw stub();
    }

    private static IllegalStateException stub() {
        return new IllegalStateException(
                "pcmc-territory API stub invoked — the real pcmc_territory mod must be installed at runtime");
    }
}
