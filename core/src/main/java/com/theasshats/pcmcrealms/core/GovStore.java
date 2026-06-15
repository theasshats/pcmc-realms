package com.theasshats.pcmcrealms.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Holds every {@link RealmGov} keyed by territory entity UUID and owns the parent/child tree
 * (scope §1, §8). The pure engine for hierarchy: {@code :mod}'s {@code GovSavedData} wraps this and
 * persists it; the resolver and evaluator read it. Server-thread only (scope §11).
 *
 * <p>The leaf→root walk lives here, not in Part 1 — Part 1 only returns the leaf; Part 2 owns the
 * ancestry (scope §2). {@link #chainFrom} is what every position-local law check builds on.
 */
public final class GovStore {

    private final Map<UUID, RealmGov> govs = new LinkedHashMap<>();

    public Optional<RealmGov> get(UUID entityId) {
        return Optional.ofNullable(govs.get(entityId));
    }

    public boolean contains(UUID entityId) {
        return govs.containsKey(entityId);
    }

    /**
     * Returns the entity's political state, creating it at {@code defaultTier} (typically
     * {@link Tier#SETTLEMENT}) if Part 2 has not seen this Part 1 entity before. New territory
     * entities start ungoverned-by-Part-2 and get a {@code RealmGov} lazily on first political act.
     */
    public RealmGov getOrCreate(UUID entityId, Tier defaultTier) {
        return govs.computeIfAbsent(entityId, id -> new RealmGov(id, defaultTier));
    }

    /** Inserts a fully-formed gov (used by persistence load). Overwrites any existing entry. */
    public void put(RealmGov gov) {
        govs.put(gov.entityId(), gov);
    }

    public Collection<RealmGov> all() {
        return List.copyOf(govs.values());
    }

    public int size() {
        return govs.size();
    }

    // --- Hierarchy ----------------------------------------------------------

    /**
     * Links {@code childId} under {@code parentId}, wiring both sides. The child must not already
     * have a different parent (detach first) and the link must not introduce a cycle.
     *
     * @throws IllegalArgumentException if either entity is unknown, the child already has a parent,
     *                                  or the link would create a cycle
     */
    public void setParent(UUID childId, UUID parentId) {
        RealmGov child = require(childId);
        RealmGov parent = require(parentId);
        if (childId.equals(parentId)) {
            throw new IllegalArgumentException("an entity cannot be its own parent: " + childId);
        }
        if (child.parentId().isPresent() && !child.parentId().get().equals(parentId)) {
            throw new IllegalArgumentException("child already has a parent; detach first: " + childId);
        }
        if (wouldCycle(childId, parentId)) {
            throw new IllegalArgumentException("link would create a cycle: " + childId + " -> " + parentId);
        }
        child.setParentId(parentId);
        parent.addChild(childId);
    }

    /** Removes the parent link for {@code childId} (at-will secession, scope §8.1), if any. */
    public void detach(UUID childId) {
        RealmGov child = govs.get(childId);
        if (child == null) return;
        child.parentId().ifPresent(pid -> get(pid).ifPresent(p -> p.removeChild(childId)));
        child.clearParent();
    }

    /**
     * Cascade-cleans Part 2 state when Part 1 reports an entity removed (scope §2 — must be handled
     * or the tree leaks dangling ids). Detaches the entity from its parent and orphans its children
     * (clears their parent link so they survive as roots), then drops its {@code RealmGov}.
     *
     * @return the ids of former children left orphaned (callers may re-evaluate a federation that
     *         dropped below its composition floor — a 2b concern)
     */
    public Set<UUID> onEntityRemoved(UUID entityId) {
        RealmGov gov = govs.remove(entityId);
        if (gov == null) return Set.of();
        gov.parentId().ifPresent(pid -> get(pid).ifPresent(p -> p.removeChild(entityId)));
        Set<UUID> orphaned = new HashSet<>(gov.childIds());
        for (UUID childId : orphaned) {
            get(childId).ifPresent(RealmGov::clearParent);
        }
        return orphaned;
    }

    /**
     * The governing chain from {@code leafId} up to its root, leaf first (scope §2). Walks the
     * {@code parentId} links with a cycle guard. If the leaf has no {@code RealmGov} yet, the chain
     * is empty (the entity exists in Part 1 but has no Part 2 political state).
     */
    public List<UUID> chainFrom(UUID leafId) {
        List<UUID> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        UUID current = leafId;
        while (current != null && govs.containsKey(current) && seen.add(current)) {
            chain.add(current);
            current = govs.get(current).parentId().orElse(null);
        }
        return chain;
    }

    private RealmGov require(UUID id) {
        RealmGov gov = govs.get(id);
        if (gov == null) {
            throw new IllegalArgumentException("unknown entity: " + id);
        }
        return gov;
    }

    /** True if making {@code parentId} the parent of {@code childId} would form a cycle. */
    private boolean wouldCycle(UUID childId, UUID parentId) {
        Set<UUID> seen = new HashSet<>();
        UUID current = parentId;
        while (current != null && seen.add(current)) {
            if (current.equals(childId)) return true;
            RealmGov gov = govs.get(current);
            current = gov == null ? null : gov.parentId().orElse(null);
        }
        return false;
    }
}
