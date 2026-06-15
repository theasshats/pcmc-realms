package com.theasshats.pcmcrealms.core;

/**
 * A pluggable exemption check for SOCIAL laws (scope §5). The SOCIAL pipeline is <em>detect
 * candidate act → run justifications → only an unjustified act raises the wanted signal</em>. A
 * justified act is a full no-op: no wanted status, no guards, no bounty.
 *
 * <p>Modelled as a general step (not PvP-specific) so future laws reuse it — CONTRABAND exempting
 * in-transit goods, TRESPASS a player fleeing combat, etc. The MVP registers self-defense and
 * outlaw-open-season (scope §5, §12 recommendation).
 */
public interface Justification {

    /** Stable id for logging / config (e.g. {@code "self_defense"}). */
    String id();

    /** True if this justification excuses the candidate act described by {@code ctx}. */
    boolean justifies(ViolationContext ctx);
}
