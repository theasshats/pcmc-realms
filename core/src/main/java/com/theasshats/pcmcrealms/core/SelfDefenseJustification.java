package com.theasshats.pcmcrealms.core;

/**
 * The primary built-in justification (scope §5, MVP): <b>the first striker is the violator.</b> A
 * candidate attack is excused when the target struck the actor within the combat window — the actor
 * is retaliating, so flagging them would punish the defender. The original aggressor stays flagged
 * from their own first strike (recorded before they were ever a target).
 *
 * @see AggressionTracker
 */
public final class SelfDefenseJustification implements Justification {

    private final AggressionTracker tracker;
    private final long combatWindowTicks;

    public SelfDefenseJustification(AggressionTracker tracker, long combatWindowTicks) {
        this.tracker = tracker;
        this.combatWindowTicks = combatWindowTicks;
    }

    @Override
    public String id() {
        return "self_defense";
    }

    @Override
    public boolean justifies(ViolationContext ctx) {
        if (ctx.target().isEmpty()) {
            return false; // victimless act — self-defense doesn't apply
        }
        java.util.UUID victim = ctx.target().get();
        // The candidate attack (actor → victim) is self-defense if the victim struck the actor in
        // the window [now-combatWindow, now] — bounded on both ends so a hit recorded after this
        // candidate (a later retaliation) can't retroactively excuse it.
        long from = ctx.tick() - combatWindowTicks;
        return tracker.hitInWindow(victim, ctx.actor(), from, ctx.tick());
    }
}
