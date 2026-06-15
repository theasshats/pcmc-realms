package com.theasshats.pcmcrealms.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leaf→root precedence resolver (scope §4) — the core of the law engine, and explicitly in
 * slice 2a scope even though 2a ships no hierarchy yet (so it's exercised with synthetic chains).
 */
class LawResolverTest {

    private final GovStore store = new GovStore();
    private final LawResolver resolver = new LawResolver(store);

    private RealmGov gov(Tier tier) {
        RealmGov g = new RealmGov(UUID.randomUUID(), tier);
        store.put(g);
        return g;
    }

    @Test
    void unsetLawResolvesEmpty() {
        RealmGov city = gov(Tier.CITY);
        assertTrue(resolver.resolve(List.of(city.entityId()), LawTypes.PVP.id()).isEmpty());
    }

    @Test
    void singleEntityLawResolves() {
        RealmGov town = gov(Tier.TOWN);
        town.setLaw(LawTypes.PVP.id(), LawTypes.PVP.serialize(PvpPolicy.DENY));

        LawResolution r = resolver.resolve(List.of(town.entityId()), LawTypes.PVP.id()).orElseThrow();
        assertEquals(town.entityId(), r.decidingEntityId());
        assertEquals(Tier.TOWN, r.decidingTier());
        assertEquals(PvpPolicy.DENY, LawTypes.PVP.parse(r.serializedValue()).orElseThrow());
    }

    @Test
    void higherTierOverridesSubordinate() {
        // city (leaf) ALLOWs, kingdom (root) DENYs -> the higher tier wins.
        RealmGov city = gov(Tier.CITY);
        RealmGov kingdom = gov(Tier.KINGDOM);
        store.setParent(city.entityId(), kingdom.entityId());
        city.setLaw(LawTypes.PVP.id(), LawTypes.PVP.serialize(PvpPolicy.ALLOW));
        kingdom.setLaw(LawTypes.PVP.id(), LawTypes.PVP.serialize(PvpPolicy.DENY));

        List<UUID> chain = store.chainFrom(city.entityId());
        assertEquals(List.of(city.entityId(), kingdom.entityId()), chain);

        LawResolution r = resolver.resolve(chain, LawTypes.PVP.id()).orElseThrow();
        assertEquals(kingdom.entityId(), r.decidingEntityId());
        assertEquals(PvpPolicy.DENY, LawTypes.PVP.parse(r.serializedValue()).orElseThrow());
    }

    @Test
    void subordinateFillsGapParentLeavesUnset() {
        // kingdom (root) has no opinion; city (leaf) sets it -> the leaf decides.
        RealmGov city = gov(Tier.CITY);
        RealmGov kingdom = gov(Tier.KINGDOM);
        store.setParent(city.entityId(), kingdom.entityId());
        city.setLaw(LawTypes.PVP.id(), LawTypes.PVP.serialize(PvpPolicy.DENY));

        LawResolution r = resolver.resolveFromLeaf(city.entityId(), LawTypes.PVP.id()).orElseThrow();
        assertEquals(city.entityId(), r.decidingEntityId());
        assertEquals(Tier.CITY, r.decidingTier());
    }

    @Test
    void deepChainResolvesHighestTier() {
        // settlement -> county -> empire, all set; empire (highest rank) wins.
        RealmGov settlement = gov(Tier.SETTLEMENT);
        RealmGov county = gov(Tier.COUNTY);
        RealmGov empire = gov(Tier.EMPIRE);
        store.setParent(settlement.entityId(), county.entityId());
        store.setParent(county.entityId(), empire.entityId());
        settlement.setLaw(LawTypes.PVP.id(), "ALLOW");
        county.setLaw(LawTypes.PVP.id(), "ALLOW");
        empire.setLaw(LawTypes.PVP.id(), "DENY");

        LawResolution r = resolver.resolveFromLeaf(settlement.entityId(), LawTypes.PVP.id()).orElseThrow();
        assertEquals(empire.entityId(), r.decidingEntityId());
        assertEquals(Tier.EMPIRE, r.decidingTier());
    }

    @Test
    void sameRankTieBreaksToLeafCloser() {
        // Two entities of the SAME tier in a chain (rare, but defended): the leaf-closer wins.
        RealmGov leaf = gov(Tier.COUNTY);
        RealmGov root = gov(Tier.COUNTY);
        store.setParent(leaf.entityId(), root.entityId());
        leaf.setLaw(LawTypes.PVP.id(), "ALLOW");
        root.setLaw(LawTypes.PVP.id(), "DENY");

        LawResolution r = resolver.resolveFromLeaf(leaf.entityId(), LawTypes.PVP.id()).orElseThrow();
        assertEquals(leaf.entityId(), r.decidingEntityId(), "same-rank tie should resolve to the leaf-closer entity");
        assertEquals(PvpPolicy.ALLOW, LawTypes.PVP.parse(r.serializedValue()).orElseThrow());
    }

    @Test
    void typedResolveValueConvenience() {
        RealmGov town = gov(Tier.TOWN);
        town.setLaw(LawTypes.TAX.id(), LawTypes.TAX.serialize(500));
        assertEquals(500, resolver.resolveValue(List.of(town.entityId()), LawTypes.TAX).orElseThrow());
        assertTrue(resolver.resolveValue(List.of(town.entityId()), LawTypes.PVP).isEmpty());
    }

    @Test
    void chainFromUngovernedLeafIsEmpty() {
        assertTrue(store.chainFrom(UUID.randomUUID()).isEmpty());
        assertFalse(resolver.resolveFromLeaf(UUID.randomUUID(), LawTypes.PVP.id()).isPresent());
    }
}
