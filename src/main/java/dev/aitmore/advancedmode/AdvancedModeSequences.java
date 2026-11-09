package dev.aitmore.advancedmode;

import java.util.List;
import java.util.Map;

import net.minecraft.util.Identifier;

/**
 * Fixed, hardcoded combo required for each control type while Advanced Mode is on (see
 * AdvancedModeManager): the other controls on the same console that must be pressed, in this
 * order, before the control itself will actually activate. Deliberately not randomised or
 * hinted anywhere in the UI - learning the combo is the point of the mode.
 *
 * Entries that aren't actually present as a control on a given console (not every console model
 * has every control) are simply skipped by AdvancedModeManager rather than making the combo
 * impossible.
 */
public class AdvancedModeSequences {

    /**
     * handbrake, throttle and monitor are deliberately never gated and never used as a combo
     * step for anything else - see AdvancedModeManager#EXEMPT_CONTROLS.
     */
    private static final List<Identifier> DEFAULT_SEQUENCE = List.of(ait("power"));

    private static final Map<Identifier, List<Identifier>> SEQUENCES = Map.ofEntries(
            Map.entry(ait("direction"), List.of(ait("power"), ait("x"))),
            Map.entry(ait("door_control"), List.of(ait("door_lock"))),
            Map.entry(ait("door_lock"), List.of(ait("door_control"))),
            Map.entry(ait("power"), List.of(ait("alarms"))),
            Map.entry(ait("shields"), List.of(ait("power"), ait("alarms"))),
            Map.entry(ait("randomiser"), List.of(ait("direction"), ait("power"))),
            Map.entry(ait("fast_return"), List.of(ait("direction"), ait("power"))),
            Map.entry(ait("telepathic_circuit"), List.of(ait("power"), ait("alarms")))
    );

    private static Identifier ait(String path) {
        return Identifier.of("ait", path);
    }

    /** Never empty - every control type gets at least the default combo. */
    public static List<Identifier> getSequence(Identifier controlId) {
        return SEQUENCES.getOrDefault(controlId, DEFAULT_SEQUENCE);
    }
}
