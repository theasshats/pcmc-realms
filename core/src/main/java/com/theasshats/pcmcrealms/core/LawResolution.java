package com.theasshats.pcmcrealms.core;

import java.util.UUID;

/**
 * The outcome of resolving one law over a governing chain (scope §4): which entity's value is in
 * force, at what tier, and the serialized value. Parse the value with the law's {@link LawType}.
 */
public record LawResolution(UUID decidingEntityId, Tier decidingTier, String serializedValue) {
}
