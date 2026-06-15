package com.theasshats.pcmcrealms.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The pluggable law-type registry (scope §4, §10). Built-in types (PVP, and the stubbed
 * TAX/STIPEND/FINE) register at startup; Part 3 (pcmc-mint) registers the <em>effects</em> for the
 * fiscal types without Part 2 knowing anything about coins. Part 2 owns the rule, scope,
 * precedence, schedule and trigger; Part 3 owns the credit/debit.
 *
 * <p>Engine-pure: this is the mechanism. The public, MC-facing facade Part 3 calls lives in
 * {@code :mod}'s {@code api} package and delegates here. Registration order is preserved so
 * command listings and tab-completion read stably.
 *
 * <p><b>Threading:</b> registration happens once at mod construction (single-threaded); lookups
 * run on the server thread. Not built for concurrent mutation.
 */
public final class LawRegistry {

    private final Map<String, LawType<?>> types = new LinkedHashMap<>();

    /**
     * Registers a law type. Throws if {@code id} is already taken — a duplicate id is a
     * programming error (two mods claiming the same law), not a runtime condition to swallow.
     */
    public <V> LawType<V> register(LawType<V> type) {
        LawType<?> existing = types.putIfAbsent(type.id(), type);
        if (existing != null) {
            throw new IllegalStateException("Law type already registered: " + type.id());
        }
        return type;
    }

    public Optional<LawType<?>> get(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public boolean contains(String id) {
        return types.containsKey(id);
    }

    /** All registered types, in registration order. */
    public Collection<LawType<?>> all() {
        return java.util.List.copyOf(types.values());
    }

    public int size() {
        return types.size();
    }
}
