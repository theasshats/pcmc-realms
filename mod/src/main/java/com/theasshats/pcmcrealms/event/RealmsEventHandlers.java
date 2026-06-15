package com.theasshats.pcmcrealms.event;

import com.theasshats.pcmcrealms.RealmsConfig;
import com.theasshats.pcmcrealms.command.RealmGovCommand;
import com.theasshats.pcmcrealms.core.LawTypes;
import com.theasshats.pcmcrealms.core.SocialDecision;
import com.theasshats.pcmcrealms.core.ViolationContext;
import com.theasshats.pcmcrealms.core.WantedTable;
import com.theasshats.pcmcrealms.data.GovSavedData;
import com.theasshats.pcmcterritory.api.TerritoryApi;
import com.theasshats.pcmcterritory.api.TerritoryEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wires the engine to the game (all on {@code NeoForge.EVENT_BUS}, server thread — scope §11):
 * the PVP combat detector (the live SOCIAL law), the coarse purge of expired wanted/aggression
 * state, the cascade cleanup on a Part 1 entity removal, and command registration.
 *
 * <p><b>A law never cancels a physical action</b> (scope §3): the combat handler observes the hit
 * and never cancels it — it only raises the wanted signal on an unjustified violation.
 */
public final class RealmsEventHandlers {

    private RealmsEventHandlers() {}

    /**
     * PVP detector. On a player→player hit in a {@code pvp=DENY} jurisdiction, an unjustified
     * attacker is marked wanted (guards then aggro, if any). Self-defense and outlaw-open-season are
     * exempt (scope §5). The damage is never cancelled.
     */
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return; // not player-sourced (mob, environment); PVP law doesn't apply
        }
        if (attacker.getUUID().equals(victim.getUUID())) {
            return; // self-damage
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }

        GovSavedData data = GovSavedData.get(level.getServer().overworld());
        long now = WantedService.now(level);

        // Jurisdiction is where the act lands (the victim's chunk). Part 1 returns the leaf; Part 2
        // builds the chain from its own parent map.
        List<UUID> leafList = TerritoryApi.resolve(level, new ChunkPos(victim.blockPosition()));
        if (!leafList.isEmpty()) {
            UUID leaf = leafList.get(0);
            List<UUID> chain = data.store().chainFrom(leaf);
            if (!chain.isEmpty()) {
                ViolationContext ctx = new ViolationContext(LawTypes.PVP.id(), leaf, chain,
                        attacker.getUUID(), Optional.of(victim.getUUID()), now);
                SocialDecision decision = data.evaluator().evaluatePvp(ctx);
                if (decision.isViolation()) {
                    WantedService.mark(level, data, leaf, attacker);
                }
            }
        }

        // Record the hit AFTER evaluation so a candidate never justifies itself; record even in
        // wilderness so a self-defense window carries into a jurisdiction if combat moves.
        data.aggression().recordHit(attacker.getUUID(), victim.getUUID(), now);
    }

    /** Coarse periodic sweep (never per-tick work beyond the cheap modulo): expire wanted + aggression. */
    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (now % RealmsConfig.purgeIntervalTicks() != 0) {
            return;
        }
        GovSavedData data = GovSavedData.get(overworld);

        List<WantedTable.Entry> expired = data.wanted().purgeExpired(now);
        for (WantedTable.Entry e : expired) {
            WantedService.clear(overworld, data, e.jurisdiction(), e.player());
        }
        if (!expired.isEmpty()) {
            data.setDirty();
        }
        data.aggression().purgeOlderThan(now - RealmsConfig.combatWindowTicks());
    }

    /** Cascade-clean Part 2's tree when Part 1 removes an entity (scope §2 — or the tree leaks ids). */
    public static void onTerritoryRemoved(TerritoryEvents.Removed event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        GovSavedData data = GovSavedData.get(server.overworld());
        data.store().onEntityRemoved(event.entity().id());
        data.setDirty();
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RealmGovCommand.register(event.getDispatcher());
    }
}
