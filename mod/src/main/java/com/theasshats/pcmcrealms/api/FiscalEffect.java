package com.theasshats.pcmcrealms.api;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * The money-movement hook Part 3 (pcmc-mint) registers (scope §10). Part 2 owns the <em>rule,
 * scope, precedence, schedule and trigger</em> for the fiscal laws (TAX/STIPEND/FINE); Part 3 owns
 * the <em>credit/debit</em>. Part 2 never references coins — it calls this interface when a fiscal
 * law fires and treats the boolean as "did the movement happen".
 *
 * <p>Registered via {@link RealmsApi#registerFiscalEffect}. Until Part 3 is installed, the default
 * is {@link #NOOP} (every movement reports failure) and the fiscal commands report
 * "requires pcmc-mint". Versioned deliberately: a breaking change here is a major bump and Part 3
 * pins a range (scope §10).
 */
public interface FiscalEffect {

    /**
     * Moves {@code amount} from a player's account into an entity's treasury — the settlement behind
     * an OFFICER {@code /realm fine} and an AUTOMATIC TAX skim.
     *
     * @return true if the funds moved; false if the payer couldn't cover it or no treasury backend exists
     */
    boolean collect(ServerLevel level, UUID payerPlayer, UUID treasuryEntityId, long amount, String reason);

    /**
     * Pays {@code amount} from an entity's treasury to a citizen — the AUTOMATIC STIPEND payout the
     * Part 2 scheduler drives (scope §6). Skips (returns false) when the treasury can't cover it.
     */
    boolean payout(ServerLevel level, UUID treasuryEntityId, UUID citizenPlayer, long amount, String reason);

    /** No-op effect used until Part 3 registers a real one: every movement fails. */
    FiscalEffect NOOP = new FiscalEffect() {
        @Override
        public boolean collect(ServerLevel level, UUID payerPlayer, UUID treasuryEntityId, long amount, String reason) {
            return false;
        }

        @Override
        public boolean payout(ServerLevel level, UUID treasuryEntityId, UUID citizenPlayer, long amount, String reason) {
            return false;
        }
    };
}
