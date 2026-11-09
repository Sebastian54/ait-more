package dev.aitmore.advancedmode;

import java.util.List;

import dev.amble.ait.core.entities.ConsoleControlEntity;

/**
 * A player's in-progress combo: the marked entities (in order), which one they need to press
 * next, and what to replay once the last one lands. markedEntities.size() may be 0 - AdvancedMode
 * Manager skips combo steps for controls that don't exist as an entity on this console, and an
 * empty combo short-circuits to an immediate pass rather than creating this at all.
 */
public final class ActiveSequence {

    private final List<ConsoleControlEntity> markedEntities;
    private final PendingControlActivation target;
    private int nextIndex;

    public ActiveSequence(List<ConsoleControlEntity> markedEntities, PendingControlActivation target) {
        this.markedEntities = markedEntities;
        this.target = target;
        this.nextIndex = 0;
    }

    public ConsoleControlEntity nextExpected() {
        return this.markedEntities.get(this.nextIndex);
    }

    public void advance() {
        this.nextIndex++;
    }

    public boolean isComplete() {
        return this.nextIndex >= this.markedEntities.size();
    }

    public List<ConsoleControlEntity> markedEntities() {
        return this.markedEntities;
    }

    public PendingControlActivation target() {
        return this.target;
    }
}
