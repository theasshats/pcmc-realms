package com.theasshats.pcmcrealms.api;

import com.theasshats.pcmcrealms.core.LawRegistry;
import com.theasshats.pcmcrealms.core.LawType;
import com.theasshats.pcmcrealms.core.LawTypes;

import java.util.Objects;

/**
 * The public, stable facade for consumers of Part 2 — chiefly Part 3 (pcmc-mint), which registers
 * the {@link FiscalEffect} for the TAX/STIPEND/FINE law types Part 2 defines (scope §10). Part 3
 * pins a version range against this surface; treat breaking changes here as a major bump.
 *
 * <p>The law registry is global (one per game), seeded with the built-in types at mod construction.
 * The fiscal effect defaults to {@link FiscalEffect#NOOP} until Part 3 registers a real backend.
 */
public final class RealmsApi {

    private static final LawRegistry LAW_REGISTRY = new LawRegistry();
    private static volatile FiscalEffect fiscalEffect = FiscalEffect.NOOP;
    private static boolean initialized = false;

    private RealmsApi() {}

    /** Registers the built-in law types. Called once from the mod constructor; idempotent. */
    public static synchronized void init() {
        if (initialized) return;
        LawTypes.registerBuiltins(LAW_REGISTRY);
        initialized = true;
    }

    /** The global pluggable law-type registry (scope §4). */
    public static LawRegistry lawRegistry() {
        return LAW_REGISTRY;
    }

    /** Registers an additional law type (for extensions beyond the built-ins). */
    public static <V> LawType<V> registerLaw(LawType<V> type) {
        return LAW_REGISTRY.register(type);
    }

    /**
     * Registers the money-movement backend (Part 3). Replaces the no-op default. Call once at
     * Part 3's startup.
     */
    public static void registerFiscalEffect(FiscalEffect effect) {
        fiscalEffect = Objects.requireNonNull(effect, "effect");
    }

    /** The current fiscal backend ({@link FiscalEffect#NOOP} until Part 3 registers one). */
    public static FiscalEffect fiscalEffect() {
        return fiscalEffect;
    }

    /** True once a real fiscal backend (pcmc-mint) is installed — fiscal commands gate on this. */
    public static boolean hasFiscalBackend() {
        return fiscalEffect != FiscalEffect.NOOP;
    }
}
