package com.theasshats.pcmcrealms.core;

import java.util.Optional;

/**
 * Orchestrates the SOCIAL pipeline (scope §5) for a candidate act: resolve the governing law over
 * the chain → if it forbids the act → run the justification pipeline → classify. Pure and stateful
 * only through the injected {@link LawResolver}/{@link JustificationPipeline}/tables, so the whole
 * decision unit-tests without Minecraft. {@code :mod} applies the side effects (mark wanted, post
 * the signal, flip the guard rank) when the result is a {@link SocialDecision#isViolation()}.
 */
public final class SocialLawEvaluator {

    private final LawResolver resolver;
    private final JustificationPipeline justifications;

    public SocialLawEvaluator(LawResolver resolver, JustificationPipeline justifications) {
        this.resolver = resolver;
        this.justifications = justifications;
    }

    /**
     * Evaluates a candidate player→player attack against the {@code pvp} law (the one SOCIAL law
     * live in 2a). DENY is the only value that forbids; ALLOW/unset → {@link SocialDecision#notGoverned()}.
     */
    public SocialDecision evaluatePvp(ViolationContext ctx) {
        Optional<LawResolution> resolved = resolver.resolve(ctx.chain(), LawTypes.PVP.id());
        if (resolved.isEmpty()) {
            return SocialDecision.notGoverned();
        }
        Optional<PvpPolicy> policy = LawTypes.PVP.parse(resolved.get().serializedValue());
        if (policy.isEmpty() || policy.get() != PvpPolicy.DENY) {
            return SocialDecision.notGoverned();
        }
        return classify(ctx, resolved.get());
    }

    /**
     * Generic classification once a law is known to forbid {@code ctx}: justified (exempt) or a
     * violation. Reusable by future SOCIAL laws (TRESPASS, CONTRABAND) once their "is this the
     * forbidding value?" check is wired.
     */
    public SocialDecision classify(ViolationContext ctx, LawResolution forbiddingResolution) {
        return justifications.firstApplicable(ctx)
                .map(j -> SocialDecision.justified(forbiddingResolution, j))
                .orElseGet(() -> SocialDecision.violation(forbiddingResolution));
    }
}
