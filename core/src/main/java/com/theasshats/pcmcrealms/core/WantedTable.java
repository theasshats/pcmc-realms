package com.theasshats.pcmcrealms.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The canonical "wanted" signal (scope §5, §12 recommendation): per-jurisdiction, the players
 * currently wanted and when their window expires (in game ticks). This is the soft-law output of
 * record — guard aggro, bounties and a kill-feed all read it; the MineColonies guard rank-flip is
 * one <em>consumer</em>, not the source of truth, so the model survives the guard API.
 *
 * <p>Pure and tick-driven; {@code :mod} persists it and feeds the current tick. Server-thread only.
 * The wanted state being all-or-nothing here (not on the colony rank) is what lets a restart not
 * strand a player as Hostile (scope §5 restore semantics): on load, the table is the truth and the
 * rank-flip is re-derived.
 */
public final class WantedTable {

    /** One wanted entry: who, in which jurisdiction, until when. */
    public record Entry(UUID jurisdiction, UUID player, long expiryTick) {}

    private final Map<UUID, Map<UUID, Long>> wanted = new HashMap<>(); // jurisdiction -> player -> expiryTick

    /**
     * Marks {@code player} wanted in {@code jurisdiction} until {@code expiryTick}. A later mark for
     * the same pair extends (or shortens) the window to the new expiry; the later call wins.
     */
    public void mark(UUID jurisdiction, UUID player, long expiryTick) {
        wanted.computeIfAbsent(jurisdiction, j -> new HashMap<>()).put(player, expiryTick);
    }

    /** True if {@code player} is currently wanted in {@code jurisdiction} at {@code now}. */
    public boolean isWanted(UUID jurisdiction, UUID player, long now) {
        Long expiry = wanted.getOrDefault(jurisdiction, Map.of()).get(player);
        return expiry != null && expiry > now;
    }

    /** Clears a player's wanted status in a jurisdiction (e.g. pardon). Returns true if it existed. */
    public boolean clear(UUID jurisdiction, UUID player) {
        Map<UUID, Long> inJur = wanted.get(jurisdiction);
        if (inJur == null) return false;
        boolean removed = inJur.remove(player) != null;
        if (inJur.isEmpty()) {
            wanted.remove(jurisdiction);
        }
        return removed;
    }

    /**
     * Drops every entry whose window has ended at {@code now}. Returns the expired entries so the
     * caller can run restore side effects (e.g. put a MineColonies rank back). Call periodically.
     */
    public List<Entry> purgeExpired(long now) {
        List<Entry> expired = new java.util.ArrayList<>();
        for (Iterator<Map.Entry<UUID, Map<UUID, Long>>> jt = wanted.entrySet().iterator(); jt.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Long>> jurEntry = jt.next();
            UUID jurisdiction = jurEntry.getKey();
            Map<UUID, Long> players = jurEntry.getValue();
            for (Iterator<Map.Entry<UUID, Long>> pt = players.entrySet().iterator(); pt.hasNext(); ) {
                Map.Entry<UUID, Long> pe = pt.next();
                if (pe.getValue() <= now) {
                    expired.add(new Entry(jurisdiction, pe.getKey(), pe.getValue()));
                    pt.remove();
                }
            }
            if (players.isEmpty()) {
                jt.remove();
            }
        }
        return expired;
    }

    /** All current entries (for persistence and {@code /realm wanted} listings). */
    public List<Entry> entries() {
        List<Entry> all = new java.util.ArrayList<>();
        wanted.forEach((jur, players) ->
                players.forEach((player, expiry) -> all.add(new Entry(jur, player, expiry))));
        return all;
    }

    public boolean isEmpty() {
        return wanted.isEmpty();
    }
}
