package com.theasshats.pcmcrealms.core;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A typed, pluggable law definition (scope §4). The registry ({@link LawRegistry}) holds these;
 * a {@link RealmGov} stores a law's <em>value</em> as a serialized string keyed by {@link #id()},
 * and the {@link LawResolver} resolves the in-force value leaf→root by tier.
 *
 * <p>Generic over the value type {@code V} (e.g. {@link PvpPolicy} for {@code pvp}, an integer rate
 * for {@code tax}). The (de)serialization round-trips through {@link #serialize}/{@link #parse} so
 * persistence (NBT in {@code :mod}) and the resolver only ever handle strings — they never need to
 * know {@code V}. This is the surface Part 3 (pcmc-mint) registers fiscal law types into.
 *
 * @param <V> the law's value type
 */
public final class LawType<V> {

    private final String id;
    private final String displayName;
    private final EnforcementMode mode;
    private final Function<String, Optional<V>> parser;
    private final Function<V, String> serializer;
    private final List<String> examples;

    private LawType(String id, String displayName, EnforcementMode mode,
                    Function<String, Optional<V>> parser, Function<V, String> serializer,
                    List<String> examples) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.examples = List.copyOf(examples);
    }

    /**
     * Builds a law type whose value is an enum, with case-insensitive parsing and the enum
     * constants offered as command suggestions. Covers PVP, SECESSION, and other ALLOW/DENY laws.
     */
    public static <E extends Enum<E>> LawType<E> ofEnum(String id, String displayName,
                                                        EnforcementMode mode, Class<E> enumClass) {
        E[] constants = enumClass.getEnumConstants();
        Function<String, Optional<E>> parser = s -> {
            for (E c : constants) {
                if (c.name().equalsIgnoreCase(s)) return Optional.of(c);
            }
            return Optional.empty();
        };
        return new LawType<>(id, displayName, mode, parser, Enum::name,
                java.util.Arrays.stream(constants).map(Enum::name).toList());
    }

    /**
     * Builds a non-negative integer law type (e.g. a TAX rate in basis points, a STIPEND amount).
     * Used for the fiscal law-type stubs registered in slice 2a; Part 3 supplies the effect.
     */
    public static LawType<Integer> ofNonNegativeInt(String id, String displayName,
                                                    EnforcementMode mode, List<String> examples) {
        Function<String, Optional<Integer>> parser = s -> {
            try {
                int v = Integer.parseInt(s.trim());
                return v >= 0 ? Optional.of(v) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        };
        return new LawType<>(id, displayName, mode, parser, String::valueOf, examples);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public EnforcementMode mode() {
        return mode;
    }

    /** Parses a stored/typed value string, empty if invalid. */
    public Optional<V> parse(String raw) {
        if (raw == null) return Optional.empty();
        return parser.apply(raw);
    }

    /** Serializes a value for storage; the inverse of {@link #parse}. */
    public String serialize(V value) {
        return serializer.apply(value);
    }

    /** Accepts a raw string only if it round-trips: parses to a value, re-serialized canonically. */
    public Optional<String> normalize(String raw) {
        return parse(raw).map(this::serialize);
    }

    /** Suggested values for command tab-completion (e.g. {@code ALLOW}, {@code DENY}). */
    public Collection<String> examples() {
        return examples;
    }

    @Override
    public String toString() {
        return "LawType[" + id + " (" + mode + ")]";
    }
}
