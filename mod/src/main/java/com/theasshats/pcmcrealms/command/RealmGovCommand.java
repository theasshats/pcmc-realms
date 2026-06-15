package com.theasshats.pcmcrealms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.theasshats.pcmcrealms.RealmsConfig;
import com.theasshats.pcmcrealms.api.RealmsApi;
import com.theasshats.pcmcrealms.core.LawResolution;
import com.theasshats.pcmcrealms.core.LawType;
import com.theasshats.pcmcrealms.core.MunicipalityMetrics;
import com.theasshats.pcmcrealms.core.PromotionResult;
import com.theasshats.pcmcrealms.core.PromotionRules;
import com.theasshats.pcmcrealms.core.RealmGov;
import com.theasshats.pcmcrealms.core.Tier;
import com.theasshats.pcmcrealms.data.GovSavedData;
import com.theasshats.pcmcrealms.event.WantedService;
import com.theasshats.pcmcterritory.api.EntitySnapshot;
import com.theasshats.pcmcterritory.api.TerritoryApi;
import com.theasshats.pcmcterritory.core.Role;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Part 2's additions to the {@code /realm} command tree (scope §9). Part 1 already ships
 * {@code found | info | whogoverns}; Brigadier merges same-named literal roots, so these slot in
 * alongside them. All gating is server-authoritative and {@link Role}-based against the entity
 * governing the player's location.
 *
 * <p>Slice 2a surface: {@code promote}, {@code law set|unset|list}, {@code wanted}, and a stubbed
 * {@code fine} (reports "requires pcmc-mint" until Part 3 registers a {@code FiscalEffect}). TAX and
 * STIPEND are configured through {@code law set tax|stipend} — registered, but inert until Part 3.
 */
public final class RealmGovCommand {

    private RealmGovCommand() {}

    private static final SuggestionProvider<CommandSourceStack> LAW_TYPE_SUGGESTIONS = (ctx, builder) -> {
        RealmsApi.lawRegistry().all().forEach(type -> builder.suggest(type.id()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> LAW_VALUE_SUGGESTIONS = (ctx, builder) -> {
        String typeId = StringArgumentType.getString(ctx, "type");
        RealmsApi.lawRegistry().get(typeId).ifPresent(type -> type.examples().forEach(builder::suggest));
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("realm")
                .then(Commands.literal("promote")
                        .executes(ctx -> promote(ctx.getSource())))
                .then(Commands.literal("law")
                        .then(Commands.literal("set")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(LAW_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .suggests(LAW_VALUE_SUGGESTIONS)
                                                .executes(ctx -> lawSet(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        StringArgumentType.getString(ctx, "value"))))))
                        .then(Commands.literal("unset")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(LAW_TYPE_SUGGESTIONS)
                                        .executes(ctx -> lawUnset(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> lawList(ctx.getSource()))))
                .then(Commands.literal("wanted")
                        .executes(ctx -> wantedList(ctx.getSource(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> wantedList(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("fine")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> fine(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"), ""))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> fine(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        StringArgumentType.getString(ctx, "reason")))))))
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("wanted")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> debugWanted(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), -1))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes(ctx -> debugWanted(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))))
                        .then(Commands.literal("pardon")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> debugPardon(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))));
    }

    // --- promote -----------------------------------------------------------

    private static int promote(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        GovSavedData data = data(level);

        EntitySnapshot entity = requireLeader(source, player, level);
        if (entity == null) {
            return 0;
        }

        RealmGov gov = data.store().getOrCreate(entity.id(), Tier.SETTLEMENT);
        MunicipalityMetrics metrics = data.metricsLookup().metricsFor(level, entity.colonyIds());
        PromotionResult result = PromotionRules.validate(gov.tier(), metrics, RealmsConfig.promotionThresholds());

        if (!result.allowed()) {
            source.sendFailure(Component.literal("Cannot promote " + entity.name() + ": "
                    + describeReasons(result, metrics)));
            return 0;
        }

        Tier target = result.targetTier().orElseThrow();
        gov.setTier(target);
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                entity.name() + " promoted to " + target.name() + "."), true);
        return 1;
    }

    // --- law set / unset / list -------------------------------------------

    private static int lawSet(CommandSourceStack source, String typeId, String rawValue) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        GovSavedData data = data(level);

        EntitySnapshot entity = requireLeader(source, player, level);
        if (entity == null) {
            return 0;
        }

        Optional<LawType<?>> type = RealmsApi.lawRegistry().get(typeId);
        if (type.isEmpty()) {
            source.sendFailure(Component.literal("Unknown law type: " + typeId));
            return 0;
        }
        Optional<String> normalized = type.get().normalize(rawValue);
        if (normalized.isEmpty()) {
            source.sendFailure(Component.literal("Invalid value '" + rawValue + "' for " + typeId
                    + " (expected one of: " + String.join(", ", type.get().examples()) + ")"));
            return 0;
        }

        RealmGov gov = data.store().getOrCreate(entity.id(), Tier.SETTLEMENT);
        gov.setLaw(typeId, normalized.get());
        data.setDirty();

        String note = fiscalNote(type.get());
        source.sendSuccess(() -> Component.literal(
                "Law " + typeId + " set to " + normalized.get() + " for " + entity.name() + "." + note), true);
        return 1;
    }

    private static int lawUnset(CommandSourceStack source, String typeId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        GovSavedData data = data(level);

        EntitySnapshot entity = requireLeader(source, player, level);
        if (entity == null) {
            return 0;
        }

        Optional<RealmGov> gov = data.store().get(entity.id());
        boolean removed = gov.map(g -> g.unsetLaw(typeId)).orElse(false);
        if (!removed) {
            source.sendFailure(Component.literal(typeId + " was not set on " + entity.name() + "."));
            return 0;
        }
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Law " + typeId + " cleared for " + entity.name() + " (falls back to parent/default)."), true);
        return 1;
    }

    private static int lawList(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        GovSavedData data = data(level);

        Optional<UUID> leaf = leafAt(level, player);
        if (leaf.isEmpty()) {
            source.sendFailure(Component.literal("No realm governs this location."));
            return 0;
        }
        List<UUID> chain = data.store().chainFrom(leaf.get());

        source.sendSuccess(() -> Component.literal("Laws in force here:"), false);
        for (LawType<?> type : RealmsApi.lawRegistry().all()) {
            Optional<LawResolution> resolved = chain.isEmpty()
                    ? Optional.empty()
                    : data.resolver().resolve(chain, type.id());
            String line = resolved
                    .map(r -> "  " + type.id() + " = " + r.serializedValue() + " (by " + r.decidingTier().name()
                            + ") [" + type.mode() + "]")
                    .orElse("  " + type.id() + " = <unset> [" + type.mode() + "]");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // --- wanted ------------------------------------------------------------

    private static int wantedList(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        GovSavedData data = data(level);
        long now = level.getServer().overworld().getGameTime();

        Optional<UUID> leaf = leafAt(level, player);
        if (leaf.isEmpty()) {
            source.sendFailure(Component.literal("No realm governs this location."));
            return 0;
        }
        UUID jurisdiction = leaf.get();

        if (target != null) {
            boolean wanted = data.wanted().isWanted(jurisdiction, target.getUUID(), now);
            source.sendSuccess(() -> Component.literal(
                    target.getGameProfile().getName() + (wanted ? " is WANTED here." : " is not wanted here.")), false);
            return 1;
        }

        long count = data.wanted().entries().stream()
                .filter(e -> e.jurisdiction().equals(jurisdiction) && e.expiryTick() > now)
                .count();
        source.sendSuccess(() -> Component.literal(count + " player(s) currently wanted in this jurisdiction."), false);
        return 1;
    }

    // --- fine (OFFICER; settlement is Part 3) ------------------------------

    private static int fine(CommandSourceStack source, ServerPlayer target, int amount, String reason)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        EntitySnapshot entity = requireRole(source, player, level, Role.OFFICER);
        if (entity == null) {
            return 0;
        }

        if (!RealmsApi.hasFiscalBackend()) {
            source.sendFailure(Component.literal(
                    "Fines require pcmc-mint (Part 3), which is not installed. The fine was not applied."));
            return 0;
        }

        boolean ok = RealmsApi.fiscalEffect().collect(level, target.getUUID(), entity.id(), amount,
                reason.isEmpty() ? "fine" : reason);
        if (!ok) {
            source.sendFailure(Component.literal(
                    "Could not collect the fine (the target may be unable to pay)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Fined " + target.getGameProfile().getName() + " " + amount + " into " + entity.name() + "."), true);
        return 1;
    }

    // --- debug (OP; makes the SOCIAL/guard pipeline testable without a second player) -----------

    /**
     * Op helper: flag a player wanted in the executor's current jurisdiction so the guard rank-flip
     * fires — the only way to exercise the §5 guard spike solo (mirrors Part 1's {@code debug bindclaim}
     * for an otherwise-unreachable path). Standing in your own colony, {@code /realm debug wanted <you>}
     * turns its guards on you. {@code seconds < 0} uses the configured wanted window.
     */
    private static int debugWanted(CommandSourceStack source, ServerPlayer target, int seconds)
            throws CommandSyntaxException {
        ServerPlayer op = source.getPlayerOrException();
        ServerLevel level = op.serverLevel();
        GovSavedData data = data(level);

        Optional<UUID> leaf = leafAt(level, op);
        if (leaf.isEmpty()) {
            source.sendFailure(Component.literal("No realm governs this location."));
            return 0;
        }
        long duration = seconds < 0 ? RealmsConfig.wantedWindowTicks() : seconds * 20L;
        WantedService.markFor(level, data, leaf.get(), target, duration);

        String jurisdiction = TerritoryApi.getEntity(level, leaf.get())
                .map(EntitySnapshot::name).orElse(leaf.get().toString());
        source.sendSuccess(() -> Component.literal("[debug] " + target.getGameProfile().getName()
                + " flagged wanted in " + jurisdiction + " for " + (duration / 20) + "s"
                + " — its guards (if any are near) will aggro."), true);
        return 1;
    }

    /** Op helper: clear a player's wanted status in the current jurisdiction and restore their rank. */
    private static int debugPardon(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer op = source.getPlayerOrException();
        ServerLevel level = op.serverLevel();
        GovSavedData data = data(level);

        Optional<UUID> leaf = leafAt(level, op);
        if (leaf.isEmpty()) {
            source.sendFailure(Component.literal("No realm governs this location."));
            return 0;
        }
        WantedService.pardon(level, data, leaf.get(), target.getUUID());
        source.sendSuccess(() -> Component.literal("[debug] pardoned "
                + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    // --- helpers -----------------------------------------------------------

    private static GovSavedData data(ServerLevel level) {
        return GovSavedData.get(level.getServer().overworld());
    }

    private static Optional<UUID> leafAt(ServerLevel level, ServerPlayer player) {
        List<UUID> chain = TerritoryApi.resolve(player.serverLevel(), new ChunkPos(player.blockPosition()));
        return chain.isEmpty() ? Optional.empty() : Optional.of(chain.get(0));
    }

    /** Resolves the entity at the player's location and checks LEADER; sends a failure and returns null otherwise. */
    private static EntitySnapshot requireLeader(CommandSourceStack source, ServerPlayer player, ServerLevel level) {
        return requireRole(source, player, level, Role.LEADER);
    }

    private static EntitySnapshot requireRole(CommandSourceStack source, ServerPlayer player, ServerLevel level,
                                              Role required) {
        Optional<UUID> leaf = leafAt(level, player);
        if (leaf.isEmpty()) {
            source.sendFailure(Component.literal("No realm governs this location."));
            return null;
        }
        Optional<EntitySnapshot> entity = TerritoryApi.getEntity(level, leaf.get());
        if (entity.isEmpty()) {
            source.sendFailure(Component.literal("No realm governs this location."));
            return null;
        }
        Role role = entity.get().members().get(player.getUUID());
        if (role == null || !role.atLeast(required)) {
            source.sendFailure(Component.literal("You must be at least " + required.name()
                    + " of " + entity.get().name() + " to do that."));
            return null;
        }
        return entity.get();
    }

    private static String describeReasons(PromotionResult result, MunicipalityMetrics metrics) {
        List<String> reasons = result.unmetReasons();
        if (reasons.contains(PromotionRules.REASON_NOT_MUNICIPALITY)) {
            return "federations are composed, not promoted.";
        }
        if (reasons.contains(PromotionRules.REASON_ALREADY_MAX_MUNICIPALITY)) {
            return "it is already a CITY (the top municipality tier); form a federation instead.";
        }
        if (reasons.contains(PromotionRules.REASON_NO_THRESHOLD_CONFIGURED)) {
            return "no threshold is configured for the next tier.";
        }
        StringBuilder sb = new StringBuilder("requirements not met (");
        boolean first = true;
        for (String r : reasons) {
            if (!first) sb.append(", ");
            first = false;
            switch (r) {
                case PromotionRules.REASON_CITIZENS -> sb.append("citizens=").append(metrics.citizens());
                case PromotionRules.REASON_CLAIMED_CHUNKS -> sb.append("chunks=").append(metrics.claimedChunks());
                case PromotionRules.REASON_TOWN_HALL_LEVEL -> sb.append("town hall=").append(metrics.townHallLevel());
                default -> sb.append(r);
            }
        }
        return sb.append(").").toString();
    }

    private static String fiscalNote(LawType<?> type) {
        boolean fiscal = type.id().equals("tax") || type.id().equals("stipend");
        if (fiscal && !RealmsApi.hasFiscalBackend()) {
            return " (note: requires pcmc-mint to take effect)";
        }
        return "";
    }
}
