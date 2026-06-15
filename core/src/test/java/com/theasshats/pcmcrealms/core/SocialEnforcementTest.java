package com.theasshats.pcmcrealms.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SOCIAL pipeline end-to-end at the engine level (scope §5): detect → justify → classify, with
 * the PVP law as the driver. This is the logic the MineColonies guard flip hangs off of in {@code :mod}.
 */
class SocialEnforcementTest {

    private static final long WINDOW = 300; // ~15s at 20 tps

    private final GovStore store = new GovStore();
    private final LawResolver resolver = new LawResolver(store);
    private final AggressionTracker aggression = new AggressionTracker();
    private final WantedTable wanted = new WantedTable();

    private final JustificationPipeline pipeline = new JustificationPipeline()
            .add(new SelfDefenseJustification(aggression, WINDOW))
            .add(new OutlawOpenSeasonJustification(wanted));
    private final SocialLawEvaluator evaluator = new SocialLawEvaluator(resolver, pipeline);

    private UUID denyPvpJurisdiction() {
        RealmGov city = new RealmGov(UUID.randomUUID(), Tier.CITY);
        city.setLaw(LawTypes.PVP.id(), LawTypes.PVP.serialize(PvpPolicy.DENY));
        store.put(city);
        return city.entityId();
    }

    private ViolationContext pvpCtx(UUID jur, UUID attacker, UUID victim, long tick) {
        return new ViolationContext(LawTypes.PVP.id(), jur, store.chainFrom(jur),
                attacker, Optional.of(victim), tick);
    }

    @Test
    void pvpAllowedIsNotGoverned() {
        RealmGov city = new RealmGov(UUID.randomUUID(), Tier.CITY);
        city.setLaw(LawTypes.PVP.id(), "ALLOW");
        store.put(city);
        SocialDecision d = evaluator.evaluatePvp(pvpCtx(city.entityId(), UUID.randomUUID(), UUID.randomUUID(), 100));
        assertEquals(SocialDecision.Kind.NOT_GOVERNED, d.kind());
    }

    @Test
    void pvpUnsetIsNotGoverned() {
        RealmGov city = new RealmGov(UUID.randomUUID(), Tier.CITY);
        store.put(city);
        SocialDecision d = evaluator.evaluatePvp(pvpCtx(city.entityId(), UUID.randomUUID(), UUID.randomUUID(), 100));
        assertEquals(SocialDecision.Kind.NOT_GOVERNED, d.kind());
    }

    @Test
    void unprovokedAttackInDenyIsAViolation() {
        UUID jur = denyPvpJurisdiction();
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        // record the attacker's own first strike (this is what makes THEM the violator)
        aggression.recordHit(attacker, victim, 1000);

        SocialDecision d = evaluator.evaluatePvp(pvpCtx(jur, attacker, victim, 1000));
        assertTrue(d.isViolation());
        assertEquals(jur, d.resolution().orElseThrow().decidingEntityId());
    }

    @Test
    void retaliationWithinWindowIsSelfDefense() {
        UUID jur = denyPvpJurisdiction();
        UUID aggressor = UUID.randomUUID();
        UUID defender = UUID.randomUUID();

        // aggressor strikes first at t=1000; defender hits back at t=1100 (within the 300t window)
        aggression.recordHit(aggressor, defender, 1000);
        SocialDecision defenderHit = evaluator.evaluatePvp(pvpCtx(jur, defender, aggressor, 1100));
        assertEquals(SocialDecision.Kind.JUSTIFIED, defenderHit.kind());
        assertEquals("self_defense", defenderHit.exemptedBy().orElseThrow().id());
    }

    @Test
    void aggressorStillFlaggedDespiteVictimRetaliating() {
        UUID jur = denyPvpJurisdiction();
        UUID aggressor = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        aggression.recordHit(aggressor, defender, 1000); // aggressor's first strike
        aggression.recordHit(defender, aggressor, 1100); // defender's retaliation

        // Evaluate the aggressor's first strike: the defender had NOT yet hit them, so unjustified.
        // (We model the original strike at its own time.)
        SocialDecision aggressorHit = evaluator.evaluatePvp(pvpCtx(jur, aggressor, defender, 1000));
        assertTrue(aggressorHit.isViolation(), "the first striker stays the violator");
    }

    @Test
    void retaliationAfterWindowIsAFreshViolation() {
        UUID jur = denyPvpJurisdiction();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        aggression.recordHit(a, b, 1000);
        // b strikes a long after the window — no longer self-defense, b is now an aggressor
        SocialDecision late = evaluator.evaluatePvp(pvpCtx(jur, b, a, 1000 + WINDOW + 1));
        assertTrue(late.isViolation());
    }

    @Test
    void attackingAWantedOutlawIsOpenSeason() {
        UUID jur = denyPvpJurisdiction();
        UUID hunter = UUID.randomUUID();
        UUID outlaw = UUID.randomUUID();
        wanted.mark(jur, outlaw, 5000); // outlaw is wanted in this jurisdiction until t=5000

        SocialDecision d = evaluator.evaluatePvp(pvpCtx(jur, hunter, outlaw, 2000));
        assertEquals(SocialDecision.Kind.JUSTIFIED, d.kind());
        assertEquals("outlaw_open_season", d.exemptedBy().orElseThrow().id());
    }

    @Test
    void higherTierDenyOverridesLeafAllow() {
        // leaf city ALLOWs, parent kingdom DENYs -> an attack in the city is still a violation.
        RealmGov city = new RealmGov(UUID.randomUUID(), Tier.CITY);
        RealmGov kingdom = new RealmGov(UUID.randomUUID(), Tier.KINGDOM);
        store.put(city);
        store.put(kingdom);
        store.setParent(city.entityId(), kingdom.entityId());
        city.setLaw(LawTypes.PVP.id(), "ALLOW");
        kingdom.setLaw(LawTypes.PVP.id(), "DENY");

        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        aggression.recordHit(attacker, victim, 50);
        SocialDecision d = evaluator.evaluatePvp(pvpCtx(city.entityId(), attacker, victim, 50));
        assertTrue(d.isViolation());
        assertEquals(kingdom.entityId(), d.resolution().orElseThrow().decidingEntityId());
    }

    @Test
    void wantedTableExpiryAndPurge() {
        UUID jur = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        wanted.mark(jur, p, 1000);
        assertTrue(wanted.isWanted(jur, p, 999));
        assertFalse(wanted.isWanted(jur, p, 1000), "expiry is exclusive at the boundary");

        List<WantedTable.Entry> expired = wanted.purgeExpired(1000);
        assertEquals(1, expired.size());
        assertEquals(p, expired.get(0).player());
        assertTrue(wanted.isEmpty());
    }

    @Test
    void aggressionPurgeKeepsMapBounded() {
        UUID a = UUID.randomUUID();
        aggression.recordHit(a, UUID.randomUUID(), 100);
        aggression.purgeOlderThan(200);
        assertEquals(0, aggression.trackedAttackers());
    }
}
