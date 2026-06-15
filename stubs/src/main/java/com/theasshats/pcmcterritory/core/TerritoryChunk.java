package com.theasshats.pcmcterritory.core;

/** STUB mirror of pcmc-territory's {@code core.TerritoryChunk} (see stubs/build.gradle). */
public record TerritoryChunk(String levelId, long packedChunkPos) {

    public static long pack(int chunkX, int chunkZ) {
        return (((long) chunkX) & 0xFFFFFFFFL) | ((((long) chunkZ) & 0xFFFFFFFFL) << 32);
    }

    public static TerritoryChunk of(String levelId, int chunkX, int chunkZ) {
        return new TerritoryChunk(levelId, pack(chunkX, chunkZ));
    }
}
