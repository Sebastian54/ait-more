package dev.aitmore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.world.ServerWorld;

import dev.aitmore.light.ExteriorLightWelder;
import dev.aitmore.particlefx.ParticleKeyframeFx;
import dev.amble.ait.core.blockentities.ExteriorBlockEntity;
import dev.amble.ait.core.tardis.ServerTardis;
import dev.amble.ait.core.tardis.Tardis;
import dev.amble.ait.core.tardis.handler.travel.TravelHandler;
import dev.amble.lib.data.CachedDirectedGlobalPos;

/**
 * Welds the exterior light block (see dev.aitmore.light) above every exterior the instant it's
 * placed. TravelHandler has three placeExterior overloads - the private
 * placeExterior(CachedDirectedGlobalPos, boolean, boolean) one actually does the placement and
 * is what every other overload (and both brand-new-TARDIS creation via postInit, and every
 * subsequent landing) ultimately calls - so hooking just this one method covers welding a new
 * TARDIS's very first exterior and re-welding after every landing uniformly, with no need to
 * separately hook TardisEvents.LANDED or anything client-facing.
 *
 * The method name is ambiguous across three overloads, so the descriptor is spelled out
 * explicitly - same lesson as the static/instance "init" overload mixup earlier: a bare method
 * name can silently bind to the wrong overload instead of failing to compile.
 */
@Mixin(TravelHandler.class)
public abstract class TravelHandlerMixin {

    @Inject(method = "placeExterior(Ldev/amble/lib/data/CachedDirectedGlobalPos;ZZ)Ldev/amble/ait/core/blockentities/ExteriorBlockEntity;",
            at = @At("RETURN"), remap = false)
    private void aitmore$weldLightBlock(CachedDirectedGlobalPos globalPos, boolean animate, boolean schedule,
            CallbackInfoReturnable<ExteriorBlockEntity> cir) {
        ExteriorBlockEntity exterior = cir.getReturnValue();

        if (exterior == null)
            return;

        ServerWorld world = globalPos.getWorld();

        if (world == null)
            return;

        TravelHandler self = (TravelHandler) (Object) this;
        Tardis tardis = self.tardis();

        if (tardis == null)
            return;

        ExteriorLightWelder.weld(world, globalPos.getPos(), tardis.getUuid());
    }

    /**
     * Force-clears any active keyframe vision override the instant the handbrake is pulled
     * (engaged), rather than relying on AnimatedTravelHandlerMixin's "animation id went null"
     * detection. Pulling the handbrake mid-flight doesn't necessarily route through that natural
     * animation-completion path in the same tick - TravelHandler#handbrake itself calls
     * cancelDemat()/crash()/rematerialize() out of band from the animation's own scheduled
     * ticking - so a vision override with no explicit "vision_duration" scheduled to expire
     * (i.e. waiting on a later keyframe or the animation's natural end to clear it) could
     * otherwise get stuck on indefinitely if the player cancels the flight before that point is
     * ever reached. Only fires on engage (value == true) - disengaging the handbrake doesn't end
     * a flight, so there's nothing to reset here.
     */
    @Inject(method = "handbrake(Z)V", at = @At("HEAD"), remap = false)
    private void aitmore$resetVisionOnHandbrake(boolean value, CallbackInfo ci) {
        if (!value)
            return;

        TravelHandler self = (TravelHandler) (Object) this;
        Tardis tardis = self.tardis();

        if (tardis instanceof ServerTardis serverTardis)
            ParticleKeyframeFx.cancelVisionForAll(serverTardis);
    }
}
