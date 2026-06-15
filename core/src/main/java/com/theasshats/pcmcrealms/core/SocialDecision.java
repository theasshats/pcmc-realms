package com.theasshats.pcmcrealms.core;

import java.util.Optional;

/**
 * The result of evaluating a candidate SOCIAL act (scope §5). One of:
 *
 * <ul>
 *   <li>{@link Kind#NOT_GOVERNED} — no law in force forbids the act here (e.g. PvP is ALLOW or
 *       unset). Nothing happens.</li>
 *   <li>{@link Kind#JUSTIFIED} — a law forbids it, but a {@link Justification} excuses this instance
 *       (self-defense, outlaw open-season). A full no-op: no wanted status.</li>
 *   <li>{@link Kind#VIOLATION} — a law forbids it and nothing excuses it. The caller raises the
 *       wanted signal (and the guard consumer aggros, if any are near).</li>
 * </ul>
 */
public record SocialDecision(Kind kind, Optional<LawResolution> resolution, Optional<Justification> exemptedBy) {

    public enum Kind { NOT_GOVERNED, JUSTIFIED, VIOLATION }

    public static SocialDecision notGoverned() {
        return new SocialDecision(Kind.NOT_GOVERNED, Optional.empty(), Optional.empty());
    }

    public static SocialDecision justified(LawResolution resolution, Justification by) {
        return new SocialDecision(Kind.JUSTIFIED, Optional.of(resolution), Optional.of(by));
    }

    public static SocialDecision violation(LawResolution resolution) {
        return new SocialDecision(Kind.VIOLATION, Optional.of(resolution), Optional.empty());
    }

    public boolean isViolation() {
        return kind == Kind.VIOLATION;
    }
}
