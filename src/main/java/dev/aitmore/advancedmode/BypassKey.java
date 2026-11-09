package dev.aitmore.advancedmode;

import java.util.UUID;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Identifies a single "this one activation is allowed through" grant - see AdvancedModeManager. */
public record BypassKey(UUID playerId, Identifier controlId, BlockPos pos) {
}
