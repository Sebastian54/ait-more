package dev.aitmore;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Server -> client packet telling a player's own client to shake its camera, since AIT's
 * ClientShakeUtil#shake(float) only ever perturbs the local client it's called on and has no
 * network-triggerable path of its own - it's driven every tick from ClientTardis's own state,
 * never remotely. See dev.aitmore.client.AitMoreClient for the receiving end.
 */
public class AitMoreNetworking {

    public static final Identifier SHAKE_PACKET = Identifier.of(AitMore.MOD_ID, "shake");
    public static final Identifier VISION_PACKET = Identifier.of(AitMore.MOD_ID, "vision");

    /**
     * @param durationTicks 0 = a single instant jolt; otherwise the client keeps re-applying
     *                      the shake every tick for this many ticks instead of once.
     */
    public static void sendShake(ServerPlayerEntity player, float intensity, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(intensity);
        buf.writeVarInt(durationTicks);
        ServerPlayNetworking.send(player, SHAKE_PACKET, buf);
    }

    /**
     * Stops an in-progress continuous shake outright (a negative duration is the marker for
     * this, distinct from 0 which means "single instant jolt"). Sent whenever the demat/mat
     * animation that triggered a shake ends for any reason - including an early abort (e.g.
     * pulling the handbrake mid-sequence) - so a shake_duration never outlives the event it
     * was tied to, rather than the client blindly finishing out whatever duration it was
     * originally told regardless of what happens afterward.
     */
    public static void sendCancelShake(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(0F);
        buf.writeVarInt(-1);
        ServerPlayNetworking.send(player, SHAKE_PACKET, buf);
    }

    /**
     * Tells a player's own client to override its fog to the given view distance (blocks),
     * replicating AIT's own power-off blindness fog (BGRendererMixin/FoggyUtils) but triggered
     * by a particle keyframe instead of power state. See dev.aitmore.client.AitMoreClient for
     * the receiving end and dev.aitmore.mixin.client.BackgroundRendererMixin for where it's
     * actually rendered.
     *
     * @param durationTicks 0 = persists until cancelled (see sendClearVision); otherwise the
     *                      client automatically reverts to normal vision after this many ticks.
     * @param rampTicks     0 = snap straight to distance instantly; otherwise the client eases
     *                      toward it over this many ticks instead.
     */
    public static void sendVision(ServerPlayerEntity player, float distance, int durationTicks, int rampTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(distance);
        buf.writeVarInt(durationTicks);
        buf.writeVarInt(rampTicks);
        ServerPlayNetworking.send(player, VISION_PACKET, buf);
    }

    /**
     * Cancels an in-progress vision override outright (a negative distance is the marker for
     * this). Sent whenever the demat/mat animation that triggered it ends for any reason -
     * including the handbrake cutting a flight short - mirroring sendCancelShake, so a
     * persistent (durationTicks == 0) vision override never outlives the event it was tied to.
     *
     * @param fadeTicks 0 = snap back to normal instantly; otherwise the client eases back over
     *                  this many ticks instead, same as the ramp-in on the way toward a
     *                  restriction (see AitMoreConfig#visionResetFadeSeconds for where this
     *                  normally comes from).
     */
    public static void sendClearVision(ServerPlayerEntity player, int fadeTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(-1F);
        buf.writeVarInt(0);
        buf.writeVarInt(fadeTicks);
        ServerPlayNetworking.send(player, VISION_PACKET, buf);
    }
}
