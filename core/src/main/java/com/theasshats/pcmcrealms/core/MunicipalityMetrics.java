package com.theasshats.pcmcrealms.core;

/**
 * The free MineColonies metrics promotion is validated against (scope §8): population, footprint,
 * development. {@code :mod} reads these via {@code EntitySnapshot.colonyIds} → MineColonies API;
 * the engine just compares them to thresholds, so promotion validation unit-tests without the game.
 *
 * @param citizens      total colony citizen count (population)
 * @param claimedChunks claimed-chunk footprint
 * @param townHallLevel Town Hall building level (development); 0 if none
 */
public record MunicipalityMetrics(int citizens, int claimedChunks, int townHallLevel) {
}
