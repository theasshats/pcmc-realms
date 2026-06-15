package com.theasshats.pcmcterritory.api;

import net.neoforged.bus.api.Event;

/**
 * STUB mirror of pcmc-territory's {@code api.TerritoryEvents} (see stubs/build.gradle).
 * Posted on {@code NeoForge.EVENT_BUS}; Part 2 listens for {@link Removed} to cascade-clean
 * its political tree and {@link Changed} to invalidate cached jurisdiction footprints.
 */
public abstract class TerritoryEvents extends Event {

    private final EntitySnapshot entity;

    protected TerritoryEvents(EntitySnapshot entity) {
        this.entity = entity;
    }

    public EntitySnapshot entity() {
        return entity;
    }

    public static final class Created extends TerritoryEvents {
        public Created(EntitySnapshot entity) {
            super(entity);
        }
    }

    public static final class Removed extends TerritoryEvents {
        public Removed(EntitySnapshot entity) {
            super(entity);
        }
    }

    public static final class Changed extends TerritoryEvents {
        public Changed(EntitySnapshot entity) {
            super(entity);
        }
    }
}
