package com.theasshats.pcmcrealms.api;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * The canonical "wanted" signal, posted on {@code NeoForge.EVENT_BUS} (scope §5, §12). This is the
 * soft-law output of record: the MineColonies guard rank-flip is one consumer, and bounties + a
 * kill-feed can subscribe later. Decoupling the signal from the guard mechanism is what makes the
 * SOCIAL model robust to the guard API and reusable across the economy pillar.
 *
 * <p>Not cancelable — the act already happened (a law never cancels an action); this only announces
 * the consequence.
 */
public abstract class WantedEvent extends Event {

    private final UUID jurisdiction;
    private final UUID player;

    protected WantedEvent(UUID jurisdiction, UUID player) {
        this.jurisdiction = jurisdiction;
        this.player = player;
    }

    /** The entity (Part 1 UUID) in whose jurisdiction the player is wanted. */
    public UUID jurisdiction() {
        return jurisdiction;
    }

    /** The wanted player. */
    public UUID player() {
        return player;
    }

    /** A player became wanted (an unjustified SOCIAL violation). */
    public static final class Marked extends WantedEvent {
        private final long expiryTick;

        public Marked(UUID jurisdiction, UUID player, long expiryTick) {
            super(jurisdiction, player);
            this.expiryTick = expiryTick;
        }

        /** Game tick at which the wanted window ends. */
        public long expiryTick() {
            return expiryTick;
        }
    }

    /** A player's wanted window ended (expired or pardoned) — consumers should restore state. */
    public static final class Cleared extends WantedEvent {
        public Cleared(UUID jurisdiction, UUID player) {
            super(jurisdiction, player);
        }
    }
}
