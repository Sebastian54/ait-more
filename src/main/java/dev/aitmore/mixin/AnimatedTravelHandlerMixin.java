package dev.aitmore.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import dev.aitmore.particlefx.ParticleKeyframe;
import dev.aitmore.particlefx.ParticleKeyframeFx;
import dev.aitmore.particlefx.ParticleKeyframeRegistry;
import dev.amble.ait.core.tardis.ServerTardis;
import dev.amble.ait.core.tardis.Tardis;
import dev.amble.ait.core.tardis.handler.travel.AnimatedTravelHandler;
import dev.amble.ait.core.tardis.handler.travel.TravelHandlerBase;

/**
 * Fires custom particle keyframes (see ParticleKeyframeLoader) at fixed tick offsets into
 * whichever demat/mat animation is currently playing for this TARDIS.
 *
 * Deliberately tracks its own elapsed-ticks-since-animation-start counter rather than reading
 * AIT's internal AnimationHolder/TardisAnimation/KeyframeTracker state (most of which is
 * protected/private) - AnimatedTravelHandler exposes the resolved animation id for a state via
 * the public getAnimationIdFor(state), which is enough to derive elapsed time ourselves
 * without needing any accessor mixins into AIT's animation internals.
 *
 * Hooked into tick(MinecraftServer), not tickAnimationProgress: tickAnimationProgress is only
 * called at all when shouldTickAnimation() is true, and even then it returns early on
 * effectively every call while an animation is still in progress ("if (state ==
 * State.LANDED) return;", "if (!this.getAnimations().isAged()) return;"). A TAIL injection
 * into tickAnimationProgress would only ever fire once, at the single tick the animation
 * finishes - and if an animation gets aborted early (e.g. the player pulls the handbrake
 * mid-demat) such that shouldTickAnimation() goes false immediately, tickAnimationProgress
 * might never even get called again, so an early-abort could be missed entirely. tick(server)
 * has no such gating or early returns; it unconditionally runs every server tick regardless of
 * animation state, and by TAIL it's already called tickAnimationProgress (and any state
 * transition that triggers) for this tick, so getState()/getAnimationIdFor read the
 * post-transition value.
 *
 * Note: AnimatedTravelHandler declares two "tick" overloads (MinecraftServer and, client-side,
 * MinecraftClient) - the method descriptor below is spelled out explicitly to target only the
 * server one, the same ambiguity that broke an earlier version of InitializableMixin when a
 * bare method name matched the wrong overload.
 */
@Mixin(AnimatedTravelHandler.class)
public abstract class AnimatedTravelHandlerMixin {

    @Unique
    private Identifier aitmore$activeAnimationId;

    @Unique
    private int aitmore$animationTicks;

    @Unique
    private int aitmore$firedKeyframeIndex;

    @Inject(method = "tick(Lnet/minecraft/server/MinecraftServer;)V", at = @At("TAIL"), remap = false)
    private void aitmore$fireParticleKeyframes(MinecraftServer server, CallbackInfo ci) {
        AnimatedTravelHandler self = (AnimatedTravelHandler) (Object) this;
        TravelHandlerBase.State state = self.getState();
        Identifier animationId = self.getAnimationIdFor(state);

        if (animationId == null) {
            // Only act on the actual transition (we were tracking something a moment ago and
            // now aren't) - without this guard, cancelActiveShake would fire every single tick
            // for as long as the TARDIS just sits idle (LANDED), spamming every player with a
            // cancel packet forever instead of once when the animation actually ends.
            if (this.aitmore$activeAnimationId != null) {
                // The animation that was just playing can finish - or get aborted early, e.g.
                // via the handbrake - in the same tick window a keyframe scheduled right at its
                // end was due, or while a continuous shake/vision override was still active on
                // the client. Cancel any active shake/vision FIRST (so they don't clobber a
                // fresh one a flushed keyframe might start) and then flush anything still
                // unfired, rather than silently dropping it or leaving the client shaking/foggy
                // from an event that's over.
                this.aitmore$cancelActiveShake(self);
                this.aitmore$cancelActiveVision(self);
                this.aitmore$flushRemainingKeyframes(self);
                this.aitmore$activeAnimationId = null;
            }

            return;
        }

        if (!animationId.equals(this.aitmore$activeAnimationId)) {
            this.aitmore$activeAnimationId = animationId;
            this.aitmore$animationTicks = 0;
            this.aitmore$firedKeyframeIndex = 0;
        }

        List<ParticleKeyframe> keyframes = ParticleKeyframeRegistry.get(animationId);

        if (!keyframes.isEmpty()) {
            Tardis tardis = self.tardis();

            if (tardis instanceof ServerTardis serverTardis) {
                while (this.aitmore$firedKeyframeIndex < keyframes.size()
                        && keyframes.get(this.aitmore$firedKeyframeIndex).tick() <= this.aitmore$animationTicks) {
                    ParticleKeyframeFx.fire(serverTardis, keyframes.get(this.aitmore$firedKeyframeIndex));
                    this.aitmore$firedKeyframeIndex++;
                }
            }
        }

        this.aitmore$animationTicks++;
    }

    @Unique
    private void aitmore$cancelActiveShake(AnimatedTravelHandler self) {
        Tardis tardis = self.tardis();

        if (tardis instanceof ServerTardis serverTardis)
            ParticleKeyframeFx.cancelShakeForAll(serverTardis);
    }

    @Unique
    private void aitmore$cancelActiveVision(AnimatedTravelHandler self) {
        Tardis tardis = self.tardis();

        if (tardis instanceof ServerTardis serverTardis)
            ParticleKeyframeFx.cancelVisionForAll(serverTardis);
    }

    @Unique
    private void aitmore$flushRemainingKeyframes(AnimatedTravelHandler self) {
        if (this.aitmore$activeAnimationId == null)
            return;

        List<ParticleKeyframe> keyframes = ParticleKeyframeRegistry.get(this.aitmore$activeAnimationId);

        if (keyframes.isEmpty() || this.aitmore$firedKeyframeIndex >= keyframes.size())
            return;

        Tardis tardis = self.tardis();

        if (!(tardis instanceof ServerTardis serverTardis))
            return;

        while (this.aitmore$firedKeyframeIndex < keyframes.size()) {
            ParticleKeyframeFx.fire(serverTardis, keyframes.get(this.aitmore$firedKeyframeIndex));
            this.aitmore$firedKeyframeIndex++;
        }
    }
}
