package com.theasshats.pcmcterritory.api;

import com.theasshats.pcmcterritory.core.ClaimKey;
import com.theasshats.pcmcterritory.core.Role;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * STUB mirror of pcmc-territory's {@code api.EntitySnapshot} (see stubs/build.gradle).
 * Part 2 extends this record <em>by association</em> (a parallel {@code Map<UUID,RealmGov>}
 * in Part 2's SavedData), never by editing it.
 */
public record EntitySnapshot(
        UUID id,
        String name,
        Map<UUID, Role> members,
        Set<Integer> colonyIds,
        Set<ClaimKey> claimKeys
) {
}
