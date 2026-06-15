package com.theasshats.pcmcrealms.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier ladder shape and municipality promotion validation (scope §8), validated on the command. */
class PromotionAndTierTest {

    private final PromotionThresholds thresholds = PromotionThresholds.defaults();

    @Test
    void tierRanksAndKinds() {
        assertEquals(0, Tier.SETTLEMENT.rank());
        assertEquals(6, Tier.EMPIRE.rank());
        assertTrue(Tier.CITY.isMunicipality());
        assertTrue(Tier.COUNTY.isFederation());
        assertEquals(Tier.VILLAGE, Tier.SETTLEMENT.next().orElseThrow());
        assertTrue(Tier.CITY.next().isEmpty(), "CITY is the top of the municipality ladder");
        assertTrue(Tier.KINGDOM.next().isEmpty(), "federation tiers aren't promoted");
    }

    @Test
    void promotionSucceedsWhenMetricsMet() {
        // SETTLEMENT -> VILLAGE needs (4 citizens, 4 chunks, TH 1) by default.
        PromotionResult r = PromotionRules.validate(Tier.SETTLEMENT,
                new MunicipalityMetrics(4, 4, 1), thresholds);
        assertTrue(r.allowed());
        assertEquals(Tier.VILLAGE, r.targetTier().orElseThrow());
        assertTrue(r.unmetReasons().isEmpty());
    }

    @Test
    void promotionReportsEachUnmetMetric() {
        PromotionResult r = PromotionRules.validate(Tier.TOWN,
                new MunicipalityMetrics(0, 0, 0), thresholds); // wants 20/40/5 for CITY
        assertFalse(r.allowed());
        assertEquals(Tier.CITY, r.targetTier().orElseThrow());
        assertTrue(r.unmetReasons().contains(PromotionRules.REASON_CITIZENS));
        assertTrue(r.unmetReasons().contains(PromotionRules.REASON_CLAIMED_CHUNKS));
        assertTrue(r.unmetReasons().contains(PromotionRules.REASON_TOWN_HALL_LEVEL));
    }

    @Test
    void cityCannotPromoteFurther() {
        PromotionResult r = PromotionRules.validate(Tier.CITY,
                new MunicipalityMetrics(999, 999, 99), thresholds);
        assertFalse(r.allowed());
        assertTrue(r.unmetReasons().contains(PromotionRules.REASON_ALREADY_MAX_MUNICIPALITY));
    }

    @Test
    void federationsAreNotPromoted() {
        PromotionResult r = PromotionRules.validate(Tier.KINGDOM,
                new MunicipalityMetrics(999, 999, 99), thresholds);
        assertFalse(r.allowed());
        assertTrue(r.unmetReasons().contains(PromotionRules.REASON_NOT_MUNICIPALITY));
    }

    @Test
    void exactBoundaryMetricsAreSufficient() {
        // requirement is >=, so meeting exactly passes
        PromotionResult r = PromotionRules.validate(Tier.VILLAGE,
                new MunicipalityMetrics(10, 16, 3), thresholds); // TOWN reqs
        assertTrue(r.allowed());
    }
}
