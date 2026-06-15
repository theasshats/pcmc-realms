package com.theasshats.pcmcrealms.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the in-force value of a law over a governing chain (scope §4). The precedence rule:
 *
 * <ul>
 *   <li><b>Highest tier rank wins.</b> A federation's law overrides a subordinate municipality's.</li>
 *   <li><b>A subordinate fills gaps its parent leaves unset.</b> If the root has no opinion, the
 *       next tier down that does decides — so an unset law cascades to the most senior entity that
 *       set it.</li>
 *   <li><b>Same-rank tie → the leaf-closer (more local) entity wins</b> (documented tie-break).
 *       Ties are rare in a tree where each step up raises the tier, but defended anyway.</li>
 * </ul>
 *
 * The chain is leaf-first ({@link GovStore#chainFrom}). Resolution is value-agnostic — it returns
 * the serialized string; the caller parses it with the {@link LawType}.
 */
public final class LawResolver {

    private final GovStore store;

    public LawResolver(GovStore store) {
        this.store = store;
    }

    /**
     * Resolves {@code lawId} over the chain. Returns the deciding entity, its tier, and the value,
     * or empty when no entity in the chain has the law set (caller falls back to the law default).
     */
    public Optional<LawResolution> resolve(List<UUID> leafToRootChain, String lawId) {
        LawResolution best = null;
        for (UUID id : leafToRootChain) {
            RealmGov gov = store.get(id).orElse(null);
            if (gov == null) continue;
            Optional<String> value = gov.rawLaw(lawId);
            if (value.isEmpty()) continue;
            // Iterating leaf→root, the first entry at a given rank is the leaf-closer one; only a
            // strictly higher rank later in the walk displaces it (highest-rank-wins, tie→leaf).
            if (best == null || gov.tier().rank() > best.decidingTier().rank()) {
                best = new LawResolution(id, gov.tier(), value.get());
            }
        }
        return Optional.ofNullable(best);
    }

    /** Convenience: resolve from a leaf id by first walking the chain. */
    public Optional<LawResolution> resolveFromLeaf(UUID leafId, String lawId) {
        return resolve(store.chainFrom(leafId), lawId);
    }

    /** Typed convenience: resolve and parse with {@code type} in one step. */
    public <V> Optional<V> resolveValue(List<UUID> leafToRootChain, LawType<V> type) {
        return resolve(leafToRootChain, type.id()).flatMap(r -> type.parse(r.serializedValue()));
    }
}
