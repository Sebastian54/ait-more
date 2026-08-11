package dev.aitmore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which players currently have /commandspy toggled on. */
public final class CommandSpyManager {

    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private CommandSpyManager() {
    }

    /** @return the new state (true = now enabled) */
    public static boolean toggle(UUID playerId) {
        if (!ENABLED.add(playerId)) {
            ENABLED.remove(playerId);
            return false;
        }
        return true;
    }

    public static boolean isEnabled(UUID playerId) {
        return ENABLED.contains(playerId);
    }

    public static void remove(UUID playerId) {
        ENABLED.remove(playerId);
    }
}
