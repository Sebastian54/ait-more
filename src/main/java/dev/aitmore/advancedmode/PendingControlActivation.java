package dev.aitmore.advancedmode;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import dev.amble.ait.core.tardis.Tardis;
import dev.amble.ait.core.tardis.control.Control;

/** Everything needed to replay a control activation Advanced Mode gated, once its combo is solved. */
public record PendingControlActivation(Control control, Tardis tardis, ServerPlayerEntity player, ServerWorld world,
        BlockPos pos, boolean inside) {
}
