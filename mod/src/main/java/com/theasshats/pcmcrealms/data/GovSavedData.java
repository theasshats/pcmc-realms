package com.theasshats.pcmcrealms.data;

import com.mojang.logging.LogUtils;
import com.theasshats.pcmcrealms.RealmsConfig;
import com.theasshats.pcmcrealms.core.AggressionTracker;
import com.theasshats.pcmcrealms.core.GovStore;
import com.theasshats.pcmcrealms.core.JustificationPipeline;
import com.theasshats.pcmcrealms.core.LawResolver;
import com.theasshats.pcmcrealms.core.OutlawOpenSeasonJustification;
import com.theasshats.pcmcrealms.core.RealmGov;
import com.theasshats.pcmcrealms.core.SelfDefenseJustification;
import com.theasshats.pcmcrealms.core.SocialLawEvaluator;
import com.theasshats.pcmcrealms.core.Tier;
import com.theasshats.pcmcrealms.core.WantedTable;
import com.theasshats.pcmcrealms.integration.GuardEnforcer;
import com.theasshats.pcmcrealms.integration.MetricsLookup;
import com.theasshats.pcmcrealms.integration.RealmsIntegrations;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-level {@link SavedData} attached to the overworld (mirrors pcmc-territory's pattern). Holds
 * Part 2's political state — the {@link GovStore} tree and the {@link WantedTable} — and the engine
 * objects that read them (resolver, evaluator, justification pipeline) plus the soft-dep adapters
 * (guard enforcer, metrics lookup). The {@link AggressionTracker} is <b>transient</b>: combat state
 * is ephemeral and intentionally not persisted (a restart clears in-flight combat windows).
 *
 * <p>Persisted: the gov tree (tier, parent, laws — children are rebuilt from parents) and the wanted
 * table (so a restart doesn't strand or forget a wanted window — scope §5). Server-thread only.
 */
public final class GovSavedData extends SavedData {

    public static final String DATA_NAME = "pcmc_realms";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final GovStore store = new GovStore();
    private final WantedTable wanted = new WantedTable();
    private final AggressionTracker aggression = new AggressionTracker();

    private final LawResolver resolver;
    private final SocialLawEvaluator evaluator;
    private final GuardEnforcer guardEnforcer;
    private final MetricsLookup metricsLookup;

    private GovSavedData() {
        this.resolver = new LawResolver(store);
        JustificationPipeline pipeline = new JustificationPipeline()
                .add(new SelfDefenseJustification(aggression, RealmsConfig.combatWindowTicks()))
                .add(new OutlawOpenSeasonJustification(wanted));
        this.evaluator = new SocialLawEvaluator(resolver, pipeline);
        this.guardEnforcer = RealmsIntegrations.createGuardEnforcer();
        this.metricsLookup = RealmsIntegrations.createMetricsLookup();
    }

    /** Always fetched from the overworld, regardless of which dimension a chunk is in. */
    public static GovSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GovSavedData::new, GovSavedData::load, null),
                DATA_NAME);
    }

    public GovStore store() {
        return store;
    }

    public WantedTable wanted() {
        return wanted;
    }

    public AggressionTracker aggression() {
        return aggression;
    }

    public LawResolver resolver() {
        return resolver;
    }

    public SocialLawEvaluator evaluator() {
        return evaluator;
    }

    public GuardEnforcer guardEnforcer() {
        return guardEnforcer;
    }

    public MetricsLookup metricsLookup() {
        return metricsLookup;
    }

    private static GovSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        GovSavedData data = new GovSavedData();

        // Pass 1: restore each gov (tier + laws). Remember parent links for a second pass so a
        // parent referenced before it's loaded still wires up.
        Map<UUID, UUID> parents = new HashMap<>();
        ListTag govsTag = tag.getList("govs", Tag.TAG_COMPOUND);
        for (int i = 0; i < govsTag.size(); i++) {
            CompoundTag g = govsTag.getCompound(i);
            try {
                UUID id = g.getUUID("id");
                Tier tier = Tier.valueOf(g.getString("tier"));
                RealmGov gov = new RealmGov(id, tier);
                ListTag lawsTag = g.getList("laws", Tag.TAG_COMPOUND);
                for (int l = 0; l < lawsTag.size(); l++) {
                    CompoundTag law = lawsTag.getCompound(l);
                    gov.setLaw(law.getString("id"), law.getString("value"));
                }
                data.store.put(gov);
                if (g.hasUUID("parent")) {
                    parents.put(id, g.getUUID("parent"));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Skipping malformed gov at index {} in {} save data: {}", i, DATA_NAME, e.toString());
            }
        }

        // Pass 2: rebuild parent/child links (children are derived, not stored).
        parents.forEach((childId, parentId) -> {
            data.store.get(childId).ifPresent(child -> data.store.get(parentId).ifPresent(parent -> {
                child.setParentId(parentId);
                parent.addChild(childId);
            }));
        });

        // Wanted table.
        ListTag wantedTag = tag.getList("wanted", Tag.TAG_COMPOUND);
        for (int i = 0; i < wantedTag.size(); i++) {
            CompoundTag w = wantedTag.getCompound(i);
            try {
                data.wanted.mark(w.getUUID("jurisdiction"), w.getUUID("player"), w.getLong("expiry"));
            } catch (RuntimeException e) {
                LOGGER.error("Skipping malformed wanted entry at index {} in {} save data: {}", i, DATA_NAME, e.toString());
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag govsTag = new ListTag();
        for (RealmGov gov : store.all()) {
            CompoundTag g = new CompoundTag();
            g.putUUID("id", gov.entityId());
            g.putString("tier", gov.tier().name());
            gov.parentId().ifPresent(p -> g.putUUID("parent", p));
            ListTag lawsTag = new ListTag();
            gov.laws().forEach((id, value) -> {
                CompoundTag law = new CompoundTag();
                law.putString("id", id);
                law.putString("value", value);
                lawsTag.add(law);
            });
            g.put("laws", lawsTag);
            govsTag.add(g);
        }
        tag.put("govs", govsTag);

        ListTag wantedTag = new ListTag();
        for (WantedTable.Entry e : wanted.entries()) {
            CompoundTag w = new CompoundTag();
            w.putUUID("jurisdiction", e.jurisdiction());
            w.putUUID("player", e.player());
            w.putLong("expiry", e.expiryTick());
            wantedTag.add(w);
        }
        tag.put("wanted", wantedTag);

        return tag;
    }
}
