package com.theasshats.pcmcrealms.core;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of a promotion attempt (scope §8). Either allowed (with the target tier), or denied
 * with machine-readable reasons {@code :mod} turns into player-facing messages. Validation is on the
 * command — never per-tick — so this is computed once per {@code /realm promote}.
 *
 * @param targetTier   the tier promotion would reach, if any (empty when promotion isn't possible)
 * @param unmetReasons reason codes for denial; empty when {@link #allowed()} is true
 */
public record PromotionResult(boolean allowed, Optional<Tier> targetTier, List<String> unmetReasons) {

    public PromotionResult {
        unmetReasons = List.copyOf(unmetReasons);
    }

    public static PromotionResult allowed(Tier targetTier) {
        return new PromotionResult(true, Optional.of(targetTier), List.of());
    }

    public static PromotionResult denied(Optional<Tier> targetTier, List<String> reasons) {
        return new PromotionResult(false, targetTier, reasons);
    }
}
