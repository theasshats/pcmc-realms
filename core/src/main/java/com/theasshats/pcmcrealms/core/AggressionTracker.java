package com.theasshats.pcmcrealms.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived record of who struck whom, the bookkeeping behind the self-defense justification
 * (scope §5: "the first striker is the violator"). On every player→player hit, note
 * {@code (attacker → victim → tick)}; a candidate attack is retaliation (and so justified) if the
 * <em>victim</em> struck the <em>attacker</em> within the combat window.
 *
 * <p>Pure and tick-driven (the caller supplies the current tick), so it unit-tests without a clock.
 * Bounded by {@link #purgeOlderThan} — call it periodically so stale pairs don't accumulate.
 * Server-thread only.
 */
public final class AggressionTracker {

    private final Map<UUID, Map<UUID, Long>> lastHit = new HashMap<>(); // attacker -> victim -> tick

    /** Records that {@code attacker} hit {@code victim} at {@code tick}. */
    public void recordHit(UUID attacker, UUID victim, long tick) {
        lastHit.computeIfAbsent(attacker, a -> new HashMap<>()).put(victim, tick);
    }

    /**
     * True if {@code attacker} hit {@code victim} within the inclusive tick window
     * {@code [fromTick, toTick]}. Used to ask "did the victim of this candidate attack strike the
     * attacker recently <em>and not in the future</em>?" — the upper bound matters because a
     * retaliation recorded <em>after</em> a first strike must not retroactively justify that first
     * strike (it only justifies the retaliation itself).
     */
    public boolean hitInWindow(UUID attacker, UUID victim, long fromTick, long toTick) {
        Long t = lastHit.getOrDefault(attacker, Map.of()).get(victim);
        return t != null && t >= fromTick && t <= toTick;
    }

    /** Drops every recorded hit strictly older than {@code cutoffTick}, keeping the map bounded. */
    public void purgeOlderThan(long cutoffTick) {
        for (Iterator<Map.Entry<UUID, Map<UUID, Long>>> it = lastHit.entrySet().iterator(); it.hasNext(); ) {
            Map<UUID, Long> victims = it.next().getValue();
            victims.values().removeIf(tick -> tick < cutoffTick);
            if (victims.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Test/diagnostic: number of attacker entries currently tracked. */
    public int trackedAttackers() {
        return lastHit.size();
    }
}
