package dev.aitmore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import dev.aitmore.advancedmode.AdvancedModeManager;
import dev.amble.ait.core.tardis.Tardis;
import dev.amble.ait.core.tardis.control.Control;

/**
 * Gates every control activation behind Advanced Mode (see AdvancedModeManager) by injecting at
 * the very head of handleRun, the single method every control path (ConsoleControlEntity,
 * ControlBlockEntity, OverloadSonicMode) routes an activation through before it ever reaches the
 * control's own runServer logic - so this one injection covers every control type uniformly
 * without needing a mixin per control implementation.
 */
@Mixin(Control.class)
public abstract class ControlMixin {

    @Inject(method = "handleRun(Ldev/amble/ait/core/tardis/Tardis;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Z)Ldev/amble/ait/core/tardis/control/Control$Result;", at = @At("HEAD"), cancellable = true, remap = false)
    private void aitmore$gateAdvancedMode(Tardis tardis, ServerPlayerEntity player, ServerWorld world, BlockPos pos,
            boolean inside, CallbackInfoReturnable<Control.Result> cir) {
        Control self = (Control) (Object) this;

        if (AdvancedModeManager.shouldGate(self, tardis, player, world, pos, inside))
            cir.setReturnValue(Control.Result.FAILURE);
    }
}
