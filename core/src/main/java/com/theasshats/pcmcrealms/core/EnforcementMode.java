package com.theasshats.pcmcrealms.core;

/**
 * How a law is enforced (scope §3 — the central reframe). The mode, not the law type,
 * decides whether the mod <em>acts</em> or merely <em>signals</em>:
 *
 * <ul>
 *   <li>{@link #AUTOMATIC} — hard. The mod applies a mechanical effect directly, reserved for
 *       government money/state (TAX, STIPEND). <b>Never blocks a physical action.</b></li>
 *   <li>{@link #OFFICER} — hybrid. A human officer triggers it; the mod settles it. The verdict
 *       is a person's, the coin movement is automatic (FINE).</li>
 *   <li>{@link #SOCIAL} — soft. The mod never cancels the action; it records the violation and
 *       raises a consequence signal (wanted status, guard aggro). No enforcer present → nothing
 *       happens. A matching act counts only if it first fails a {@link Justification} check.</li>
 * </ul>
 *
 * Rule of thumb for slotting a future law: does it move government money, or restrain a player's
 * action? Money → AUTOMATIC (or OFFICER if it needs a human verdict first); action → SOCIAL.
 */
public enum EnforcementMode {
    AUTOMATIC,
    OFFICER,
    SOCIAL
}
