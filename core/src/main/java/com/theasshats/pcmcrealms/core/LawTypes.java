package com.theasshats.pcmcrealms.core;

import java.util.List;

/**
 * The built-in law types (scope §4 catalog) and a helper to register them. Their <em>ids</em> are
 * the stable contract Part 3 keys effects to — do not rename without a major bump (scope §10).
 *
 * <p>MVP (slice 2a): only {@link #PVP} is enforced end-to-end. {@link #TAX}, {@link #STIPEND} and
 * {@link #FINE} are registered so the registry contract is exercised early, but Part 2 ships no
 * coin movement for them — Part 3 (pcmc-mint) registers the fiscal effect against these ids. Until
 * then their commands report "requires pcmc-mint".
 */
public final class LawTypes {

    private LawTypes() {}

    /** Player-vs-player policy (SOCIAL). The one law enforced end-to-end in 2a. */
    public static final LawType<PvpPolicy> PVP =
            LawType.ofEnum("pvp", "PvP", EnforcementMode.SOCIAL, PvpPolicy.class);

    /** Transaction tax rate in basis points (AUTOMATIC). Type stubbed in 2a; effect is Part 3. */
    public static final LawType<Integer> TAX =
            LawType.ofNonNegativeInt("tax", "Tax (basis points)", EnforcementMode.AUTOMATIC,
                    List.of("0", "250", "500", "1000"));

    /** Per-citizen stipend amount per scheduler period (AUTOMATIC). Type stubbed in 2a; effect is Part 3. */
    public static final LawType<Integer> STIPEND =
            LawType.ofNonNegativeInt("stipend", "Stipend (amount per period)", EnforcementMode.AUTOMATIC,
                    List.of("0", "10", "50", "100"));

    /** Default fine amount for the OFFICER-issued {@code /realm fine} action. Settlement is Part 3. */
    public static final LawType<Integer> FINE =
            LawType.ofNonNegativeInt("fine", "Fine (default amount)", EnforcementMode.OFFICER,
                    List.of("10", "50", "100", "500"));

    /** Registers every built-in type into {@code registry}, in catalog order. */
    public static void registerBuiltins(LawRegistry registry) {
        registry.register(PVP);
        registry.register(TAX);
        registry.register(STIPEND);
        registry.register(FINE);
    }
}
