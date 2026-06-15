package com.theasshats.pcmcrealms.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hierarchy mechanics: parent/child wiring, the leaf→root walk, cycle guard, cascade cleanup. */
class GovStoreTest {

    private final GovStore store = new GovStore();

    private UUID create(Tier tier) {
        UUID id = UUID.randomUUID();
        store.put(new RealmGov(id, tier));
        return id;
    }

    @Test
    void getOrCreateIsLazyAndStable() {
        UUID id = UUID.randomUUID();
        assertFalse(store.contains(id));
        RealmGov a = store.getOrCreate(id, Tier.SETTLEMENT);
        RealmGov b = store.getOrCreate(id, Tier.CITY); // existing wins; default ignored
        assertEquals(a, b);
        assertEquals(Tier.SETTLEMENT, b.tier());
    }

    @Test
    void setParentWiresBothSides() {
        UUID child = create(Tier.CITY);
        UUID parent = create(Tier.KINGDOM);
        store.setParent(child, parent);
        assertEquals(parent, store.get(child).orElseThrow().parentId().orElseThrow());
        assertTrue(store.get(parent).orElseThrow().childIds().contains(child));
    }

    @Test
    void detachIsAtWill() {
        UUID child = create(Tier.CITY);
        UUID parent = create(Tier.KINGDOM);
        store.setParent(child, parent);
        store.detach(child);
        assertTrue(store.get(child).orElseThrow().parentId().isEmpty());
        assertFalse(store.get(parent).orElseThrow().childIds().contains(child));
    }

    @Test
    void rejectsSelfParentAndUnknownAndDoubleParent() {
        UUID a = create(Tier.CITY);
        UUID b = create(Tier.KINGDOM);
        UUID c = create(Tier.KINGDOM);
        assertThrows(IllegalArgumentException.class, () -> store.setParent(a, a));
        assertThrows(IllegalArgumentException.class, () -> store.setParent(a, UUID.randomUUID()));
        store.setParent(a, b);
        assertThrows(IllegalArgumentException.class, () -> store.setParent(a, c), "double-parent must be rejected");
    }

    @Test
    void rejectsCycles() {
        UUID a = create(Tier.VILLAGE);
        UUID b = create(Tier.TOWN);
        UUID c = create(Tier.CITY);
        store.setParent(a, b);
        store.setParent(b, c);
        // c -> a would close the loop a -> b -> c -> a
        assertThrows(IllegalArgumentException.class, () -> store.setParent(c, a));
    }

    @Test
    void chainFromWalksLeafToRoot() {
        UUID leaf = create(Tier.SETTLEMENT);
        UUID mid = create(Tier.COUNTY);
        UUID root = create(Tier.EMPIRE);
        store.setParent(leaf, mid);
        store.setParent(mid, root);
        assertEquals(List.of(leaf, mid, root), store.chainFrom(leaf));
    }

    @Test
    void onEntityRemovedCascadeCleans() {
        // root over {leaf}, and leaf over {grandchild}. Removing leaf must detach it from root and
        // orphan grandchild (so the tree leaks no dangling ids — scope §2).
        UUID root = create(Tier.KINGDOM);
        UUID leaf = create(Tier.CITY);
        UUID grandchild = create(Tier.TOWN);
        store.setParent(leaf, root);
        store.setParent(grandchild, leaf);

        Set<UUID> orphaned = store.onEntityRemoved(leaf);

        assertFalse(store.contains(leaf));
        assertFalse(store.get(root).orElseThrow().childIds().contains(leaf), "parent must drop the removed child");
        assertEquals(Set.of(grandchild), orphaned);
        assertTrue(store.get(grandchild).orElseThrow().parentId().isEmpty(), "orphan must lose its parent link");
    }

    @Test
    void removingUnknownEntityIsNoOp() {
        assertTrue(store.onEntityRemoved(UUID.randomUUID()).isEmpty());
    }
}
