package com.theasshats.pcmcrealms.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Law-type (de)serialization and the pluggable registry contract Part 3 builds on. */
class LawTypeAndRegistryTest {

    @Test
    void enumLawParsesCaseInsensitivelyAndRoundTrips() {
        assertEquals(PvpPolicy.DENY, LawTypes.PVP.parse("deny").orElseThrow());
        assertEquals(PvpPolicy.ALLOW, LawTypes.PVP.parse("ALLOW").orElseThrow());
        assertTrue(LawTypes.PVP.parse("nonsense").isEmpty());
        assertEquals("DENY", LawTypes.PVP.normalize("deny").orElseThrow());
        assertTrue(LawTypes.PVP.examples().contains("DENY"));
    }

    @Test
    void intLawRejectsNegativeAndNonNumeric() {
        assertEquals(500, LawTypes.TAX.parse("500").orElseThrow());
        assertTrue(LawTypes.TAX.parse("-1").isEmpty());
        assertTrue(LawTypes.TAX.parse("abc").isEmpty());
        assertEquals("500", LawTypes.TAX.normalize(" 500 ").orElseThrow(), "whitespace tolerated, canonicalized");
    }

    @Test
    void enforcementModesMatchTheTaxonomy() {
        assertEquals(EnforcementMode.SOCIAL, LawTypes.PVP.mode());
        assertEquals(EnforcementMode.AUTOMATIC, LawTypes.TAX.mode());
        assertEquals(EnforcementMode.AUTOMATIC, LawTypes.STIPEND.mode());
        assertEquals(EnforcementMode.OFFICER, LawTypes.FINE.mode());
    }

    @Test
    void registryRegistersBuiltinsInOrder() {
        LawRegistry registry = new LawRegistry();
        LawTypes.registerBuiltins(registry);
        assertEquals(4, registry.size());
        assertTrue(registry.contains("pvp"));
        assertTrue(registry.contains("tax"));
        assertTrue(registry.contains("stipend"));
        assertTrue(registry.contains("fine"));
        assertEquals("pvp", registry.all().iterator().next().id(), "PVP registered first (catalog order)");
    }

    @Test
    void duplicateRegistrationIsAProgrammingError() {
        LawRegistry registry = new LawRegistry();
        registry.register(LawTypes.PVP);
        assertThrows(IllegalStateException.class, () -> registry.register(LawTypes.PVP));
    }

    @Test
    void unknownLawLookupIsEmpty() {
        LawRegistry registry = new LawRegistry();
        assertFalse(registry.get("nope").isPresent());
    }
}
