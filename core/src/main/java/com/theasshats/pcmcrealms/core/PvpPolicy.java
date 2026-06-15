package com.theasshats.pcmcrealms.core;

/**
 * Value of the {@code pvp} law (scope §4). {@link #DENY} makes an unprovoked player→player attack
 * in the jurisdiction a SOCIAL violation (wanted + guard aggro, after the {@link Justification}
 * check); {@link #ALLOW} is the default-permissive state. The act is never cancelled either way —
 * DENY only governs the consequence.
 */
public enum PvpPolicy {
    ALLOW,
    DENY
}
