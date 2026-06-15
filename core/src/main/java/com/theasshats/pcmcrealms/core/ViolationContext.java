package com.theasshats.pcmcrealms.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The facts a {@link Justification} needs to decide whether a candidate SOCIAL act is excused
 * (scope §5). Engine-pure (UUIDs + a tick) so the whole justification pipeline unit-tests without
 * Minecraft. {@code :mod} builds one of these from a combat event.
 *
 * @param lawId        the law being evaluated (e.g. {@code "pvp"})
 * @param leaf         the leaf jurisdiction the act occurred in (the entity governing the chunk)
 * @param chain        the full governing chain leaf→root (for justifications that consider ancestry)
 * @param actor        the player performing the candidate act (the would-be violator)
 * @param target       the player acted upon, if any (PvP victim); empty for victimless acts
 * @param tick         the current game tick
 */
public record ViolationContext(
        String lawId,
        UUID leaf,
        List<UUID> chain,
        UUID actor,
        Optional<UUID> target,
        long tick
) {
    public ViolationContext {
        chain = List.copyOf(chain);
    }
}
