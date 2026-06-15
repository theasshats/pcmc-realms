package com.theasshats.pcmcrealms.core;

/**
 * The second built-in justification (scope §5, MVP — "open season"): attacking a player who is
 * <b>already wanted in this jurisdiction</b> is sanctioned, not a crime. Citizens become effective
 * deputies and it pairs naturally with bounties. Same {@link WantedTable} lookup as the wanted
 * signal itself, so no new state.
 */
public final class OutlawOpenSeasonJustification implements Justification {

    private final WantedTable wantedTable;

    public OutlawOpenSeasonJustification(WantedTable wantedTable) {
        this.wantedTable = wantedTable;
    }

    @Override
    public String id() {
        return "outlaw_open_season";
    }

    @Override
    public boolean justifies(ViolationContext ctx) {
        return ctx.target()
                .map(victim -> wantedTable.isWanted(ctx.leaf(), victim, ctx.tick()))
                .orElse(false);
    }
}
