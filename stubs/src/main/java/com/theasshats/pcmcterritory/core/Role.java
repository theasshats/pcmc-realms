package com.theasshats.pcmcterritory.core;

/**
 * STUB mirror of pcmc-territory's {@code core.Role} (see stubs/build.gradle).
 * Surfaced through {@code api.EntitySnapshot.members()} — the documented "api leaks
 * core" boundary issue Part 2 files back to Part 1 (scope §2 / §12).
 */
public enum Role {
    CITIZEN,
    OFFICER,
    LEADER;

    public boolean atLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
