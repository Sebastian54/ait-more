package dev.aitmore.particlefx;

import org.jetbrains.annotations.Nullable;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;

/**
 * A single fx event at a fixed tick offset into a named demat/mat animation: spawning a
 * particle, shaking nearby players' cameras, and/or shoving them away from the console, any
 * combination of which can be present on the same keyframe. {@code tick} is derived from a
 * JSON "time" value in seconds (× 20), matching the same seconds-to-ticks convention AIT's own
 * Blockbench keyframe parser uses for bone/timeline keys - NOT the (unused, purely cosmetic)
 * top-level "animation_length" field some Blockbench exports carry.
 *
 * @param particle null if this keyframe doesn't spawn a particle (shake/knockback-only entries
 *                 are valid)
 * @param shake    0 = no shake; otherwise forwarded as-is to AIT's own
 *                 {@code ClientShakeUtil#shake(float)} on affected clients
 * @param shakeDurationTicks 0 = a single instant jolt (the original behaviour); otherwise the
 *                 client re-applies the shake every tick for this many ticks instead of once
 * @param knockback 0 = no push; otherwise the horizontal (plus a bit of lift) velocity applied
 *                  to players in the interior, direction away from the nearest console
 * @param visionDistance -1 = not set (don't touch vision this keyframe); otherwise a fog
 *                 override applied to every player in the interior, replicating AIT's own
 *                 power-off "blindness" fog (BGRendererMixin/FoggyUtils) but keyframe-triggered
 *                 instead of power-triggered - the value is how many blocks ahead you can still
 *                 see (e.g. 2 or 3 for a tight blindness effect). Stays applied until either
 *                 visionDurationTicks elapses or the animation ends, whichever comes first.
 * @param visionDurationTicks 0 = persists until the animation itself ends (a later keyframe
 *                 changing visionDistance also overrides it); otherwise it automatically clears
 *                 back to normal vision after this many ticks, for a temporary blindness pulse
 *                 rather than a persistent one. Ignored if visionDistance isn't set.
 * @param visionRampTicks 0 = snap straight to visionDistance instantly; otherwise the client
 *                 eases toward it over this many ticks (from whatever vision distance was
 *                 already in effect, or from an unrestricted distance if nothing was), the same
 *                 gradual feel as AIT's own power-off fog ramping in over its powerDelta rather
 *                 than snapping to full blindness the instant power drops. Ignored if
 *                 visionDistance isn't set.
 */
public record ParticleKeyframe(int tick, @Nullable ParticleEffect particle, Vec3d offset, int count, Vec3d spread,
        double speed, float shake, int shakeDurationTicks, float knockback, float visionDistance,
        int visionDurationTicks, int visionRampTicks) {
}
