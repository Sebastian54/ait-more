package dev.aitmore.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.MathHelper;

import dev.aitmore.AitMoreNetworking;
import dev.aitmore.config.AitMoreConfigLoader;
import dev.amble.ait.client.util.ClientShakeUtil;

/**
 * ait-more's client entrypoint - only ever invoked by Fabric Loader when running on a physical
 * client, never on a dedicated server, so referencing client-only classes here (ClientShakeUtil,
 * MinecraftClient via the packet handler) is safe as long as nothing server-reachable imports
 * this class or anything in this package.
 */
public class AitMoreClient implements ClientModInitializer {

    /**
     * State for an in-progress continuous shake, ticked down once per client tick by the
     * single persistent listener below. A one-shot shake (durationTicks == 0 on the packet)
     * never touches this - it just calls ClientShakeUtil#shake once directly, same as before.
     * A newer shake packet arriving mid-countdown simply replaces these values outright,
     * rather than stacking with whatever was still running.
     */
    private static float activeShakeIntensity = 0F;
    private static int shakeTicksRemaining = 0;

    /** Stands in for "unrestricted" as a ramp start point when nothing was previously active - far enough that no fog is visibly applied. */
    private static final float VISION_UNRESTRICTED = 9999F;

    /**
     * visionRampTo < 0 = no override active (normal vanilla/AIT fog) - the only field
     * getActiveVisionDistance() actually needs to check. The ramp fields let a newly-received
     * vision packet ease from whatever was already showing (or from VISION_UNRESTRICTED if
     * nothing was) toward the new target over visionRampTotalTicks, instead of snapping
     * instantly. visionTicksRemaining only counts down when visionHasDuration is true - a
     * duration of 0 means "persist until a clear/replace packet arrives", not "expire
     * immediately", so it must never be decremented otherwise.
     */
    private static float visionRampFrom = -1F;
    private static float visionRampTo = -1F;
    private static int visionRampTotalTicks = 0;
    private static int visionRampTicksElapsed = 0;
    private static int visionTicksRemaining = 0;
    private static boolean visionHasDuration = false;

    /** True while ramping toward VISION_UNRESTRICTED as part of a fade-out - once the ramp completes, the override is dropped outright instead of just sitting at a very large "distance". */
    private static boolean visionClearingAfterRamp = false;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(AitMoreNetworking.SHAKE_PACKET, (client, handler, buf, responseSender) -> {
            float intensity = buf.readFloat();
            int durationTicks = buf.readVarInt();

            client.execute(() -> {
                if (durationTicks < 0) {
                    shakeTicksRemaining = 0;
                    return;
                }

                if (durationTicks == 0) {
                    ClientShakeUtil.shake(intensity);
                    return;
                }

                activeShakeIntensity = intensity;
                shakeTicksRemaining = durationTicks;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AitMoreNetworking.VISION_PACKET, (client, handler, buf, responseSender) -> {
            float distance = buf.readFloat();
            int durationTicks = buf.readVarInt();
            int rampTicks = buf.readVarInt();

            client.execute(() -> {
                if (distance < 0F) {
                    beginClearRamp(rampTicks);
                    return;
                }

                // Ease from wherever vision currently sits (or from "unrestricted" if nothing
                // was active) toward the new target, rather than snapping to it outright.
                float current = getActiveVisionDistance();
                visionRampFrom = current < 0F ? VISION_UNRESTRICTED : current;
                visionRampTo = distance;
                visionRampTotalTicks = Math.max(rampTicks, 0);
                visionRampTicksElapsed = 0;
                visionClearingAfterRamp = false;
                visionHasDuration = durationTicks > 0;
                visionTicksRemaining = durationTicks;
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (shakeTicksRemaining > 0) {
                ClientShakeUtil.shake(activeShakeIntensity);
                shakeTicksRemaining--;
            }

            if (visionRampTo >= 0F && visionRampTicksElapsed < visionRampTotalTicks)
                visionRampTicksElapsed++;

            if (visionClearingAfterRamp && visionRampTicksElapsed >= visionRampTotalTicks) {
                visionRampFrom = -1F;
                visionRampTo = -1F;
                visionRampTotalTicks = 0;
                visionRampTicksElapsed = 0;
                visionClearingAfterRamp = false;
            }

            if (visionHasDuration && visionTicksRemaining > 0) {
                visionTicksRemaining--;

                if (visionTicksRemaining == 0) {
                    visionHasDuration = false;
                    beginClearRamp(Math.round(AitMoreConfigLoader.get().visionResetFadeSeconds * 20F));
                }
            }
        });
    }

    /**
     * Starts (or performs instantly, if fadeTicks <= 0) a fade of the current vision override
     * back to normal. Used both for an explicit clear packet (handbrake, animation ending) and
     * for a keyframe's own visionDurationTicks expiring locally on the client - either way, the
     * result should ease back to normal the same way it eased in, not snap.
     */
    private static void beginClearRamp(int fadeTicks) {
        visionHasDuration = false;
        visionTicksRemaining = 0;

        if (fadeTicks <= 0) {
            visionRampFrom = -1F;
            visionRampTo = -1F;
            visionRampTotalTicks = 0;
            visionRampTicksElapsed = 0;
            visionClearingAfterRamp = false;
            return;
        }

        float current = getActiveVisionDistance();

        if (current < 0F) {
            visionClearingAfterRamp = false;
            return;
        }

        visionRampFrom = current;
        visionRampTo = VISION_UNRESTRICTED;
        visionRampTotalTicks = fadeTicks;
        visionRampTicksElapsed = 0;
        visionClearingAfterRamp = true;
    }

    /** -1 = no override; read every frame by BackgroundRendererMixin. */
    public static float getActiveVisionDistance() {
        if (visionRampTo < 0F)
            return -1F;

        if (visionRampTotalTicks <= 0)
            return visionRampTo;

        float progress = Math.min(1F, visionRampTicksElapsed / (float) visionRampTotalTicks);
        return MathHelper.lerp(progress, visionRampFrom, visionRampTo);
    }
}
