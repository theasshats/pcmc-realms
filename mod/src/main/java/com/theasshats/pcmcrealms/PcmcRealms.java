package com.theasshats.pcmcrealms;

import com.mojang.logging.LogUtils;
import com.theasshats.pcmcrealms.api.RealmsApi;
import com.theasshats.pcmcrealms.event.RealmsEventHandlers;
import com.theasshats.pcmcrealms.integration.RealmsIntegrations;
import com.theasshats.pcmcterritory.api.TerritoryEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Mod entry point for Part 2 — government (scope §1). Seeds the law registry, registers the server
 * config, and wires the engine to the game bus: PVP enforcement, the wanted/aggression purge, the
 * Part 1 entity-removal cascade, and the {@code /realm} command additions. Hard-depends on
 * pcmc_territory (the claim-resolution substrate); soft-depends on MineColonies (guard teeth).
 */
@Mod(PcmcRealms.MOD_ID)
public final class PcmcRealms {

    public static final String MOD_ID = "pcmc_realms";

    private static final Logger LOGGER = LogUtils.getLogger();

    public PcmcRealms(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, RealmsConfig.SPEC);
        RealmsApi.init();

        NeoForge.EVENT_BUS.addListener(RealmsEventHandlers::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RealmsEventHandlers::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RealmsEventHandlers::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        // Listen for Part 1 entity removals to cascade-clean Part 2's political tree (scope §2).
        NeoForge.EVENT_BUS.addListener((TerritoryEvents.Removed event) ->
                RealmsEventHandlers.onTerritoryRemoved(event));
    }

    private void onServerStarting(ServerStartingEvent event) {
        boolean mineColonies = RealmsIntegrations.mineColoniesPresent();
        LOGGER.info("[{}] MineColonies {} - SOCIAL guard enforcement {} (wanted signal always active)",
                MOD_ID, mineColonies ? "found" : "not found", mineColonies ? "enabled" : "disabled");
        LOGGER.info("[{}] Fiscal backend (pcmc-mint) {} - TAX/STIPEND/FINE {}", MOD_ID,
                RealmsApi.hasFiscalBackend() ? "present" : "absent",
                RealmsApi.hasFiscalBackend() ? "active" : "stubbed");
    }
}
