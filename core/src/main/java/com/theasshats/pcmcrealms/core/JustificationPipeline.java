package com.theasshats.pcmcrealms.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs the registered {@link Justification}s in order and reports the first that excuses a candidate
 * act (scope §5). The first match short-circuits — order is registration order, so cheaper / more
 * fundamental checks (self-defense) go first.
 */
public final class JustificationPipeline {

    private final List<Justification> justifications = new ArrayList<>();

    public JustificationPipeline add(Justification justification) {
        justifications.add(justification);
        return this;
    }

    /** The first justification that excuses {@code ctx}, or empty if the act is unjustified. */
    public Optional<Justification> firstApplicable(ViolationContext ctx) {
        for (Justification j : justifications) {
            if (j.justifies(ctx)) {
                return Optional.of(j);
            }
        }
        return Optional.empty();
    }

    public boolean isJustified(ViolationContext ctx) {
        return firstApplicable(ctx).isPresent();
    }

    public int size() {
        return justifications.size();
    }
}
