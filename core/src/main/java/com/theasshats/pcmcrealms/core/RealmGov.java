package com.theasshats.pcmcrealms.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Part 2's <em>political</em> state for a territory entity, keyed externally by that entity's
 * Part 1 UUID (scope §1: "hangs political state off that same UUID in its own SavedData"). This
 * extends Part 1's {@code EntitySnapshot} <b>by association</b> — Part 2 never edits Part 1's record.
 *
 * <p>Mutable holder; {@code :mod}'s {@code GovSavedData} (de)serializes it to NBT. The pure engine
 * (resolver, evaluator) reads it. Not thread-safe — all access is server-thread only (scope §11).
 *
 * <p>Law values are stored as serialized strings keyed by {@link LawType#id()} so persistence and
 * the resolver stay type-agnostic; typed access goes through the law's {@code LawType}.
 */
public final class RealmGov {

    private final UUID entityId;
    private Tier tier;
    private UUID parentId;                          // null = top of its tree (no federation above)
    private final Set<UUID> childIds = new LinkedHashSet<>();
    private final Map<String, String> laws = new LinkedHashMap<>();

    public RealmGov(UUID entityId, Tier tier) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    public UUID entityId() {
        return entityId;
    }

    public Tier tier() {
        return tier;
    }

    public void setTier(Tier tier) {
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    public Optional<UUID> parentId() {
        return Optional.ofNullable(parentId);
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public void clearParent() {
        this.parentId = null;
    }

    /** Live, unmodifiable view of child entity ids (federation members / carved sub-regions). */
    public Set<UUID> childIds() {
        return Collections.unmodifiableSet(childIds);
    }

    public void addChild(UUID childId) {
        childIds.add(Objects.requireNonNull(childId, "childId"));
    }

    public boolean removeChild(UUID childId) {
        return childIds.remove(childId);
    }

    // --- Laws ---------------------------------------------------------------

    /** The raw serialized value of a law set on this entity, if any (does NOT walk to parents). */
    public Optional<String> rawLaw(String lawId) {
        return Optional.ofNullable(laws.get(lawId));
    }

    /** Sets (or replaces) a law's value on this entity. The value must already be normalized. */
    public void setLaw(String lawId, String serializedValue) {
        laws.put(Objects.requireNonNull(lawId, "lawId"), Objects.requireNonNull(serializedValue, "value"));
    }

    /** Clears a law on this entity so resolution falls back to a parent (or the default). */
    public boolean unsetLaw(String lawId) {
        return laws.remove(lawId) != null;
    }

    /** Unmodifiable view of all laws set directly on this entity (lawId → serialized value). */
    public Map<String, String> laws() {
        return Collections.unmodifiableMap(laws);
    }
}
