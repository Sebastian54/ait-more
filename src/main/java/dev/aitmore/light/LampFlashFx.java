package dev.aitmore.light;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import dev.aitmore.AitMoreNetworking;
import dev.aitmore.config.AitMoreConfig;
import dev.aitmore.config.AitMoreConfigLoader;
import dev.aitmore.particlefx.ParticleKeyframe;
import dev.amble.ait.core.tardis.ServerTardis;
import dev.amble.lib.data.CachedDirectedGlobalPos;

/**
 * Fires a keyframe's "lamp_flash" at the exterior (not the interior, unlike the rest of the
 * keyframe fx): pulses the welded light block for ground light, and tells nearby clients to draw
 * the lamp glow and halo (see dev.aitmore.client.LampFlashClient). A "lamp_delay" holds the flash
 * back, at the exterior's position as it was when the keyframe fired, so it can land after the
 * animation (and, on takeoff, the exterior itself) is gone.
 */
public class LampFlashFx {

    private static final double BROADCAST_RADIUS = 128.0;

    private static final class Pending {
        final UUID tardisId;
        final ServerWorld world;
        final BlockPos pos;
        final ParticleKeyframe keyframe;
        int ticksLeft;

        Pending(UUID tardisId, ServerWorld world, BlockPos pos, ParticleKeyframe keyframe) {
            this.tardisId = tardisId;
            this.world = world;
            this.pos = pos;
            this.keyframe = keyframe;
            this.ticksLeft = keyframe.lampDelayTicks();
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Pending> iterator = PENDING.iterator();

            while (iterator.hasNext()) {
                Pending pending = iterator.next();

                if (--pending.ticksLeft <= 0) {
                    iterator.remove();
                    flash(pending.tardisId, pending.world, pending.pos, pending.keyframe);
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    public static void fire(ServerTardis tardis, ParticleKeyframe keyframe) {
        if (!AitMoreConfigLoader.get().lampFlashEnabled)
            return;

        CachedDirectedGlobalPos position = tardis.travel().position();

        if (position == null)
            return;

        ServerWorld world = position.getWorld();
        BlockPos pos = position.getPos();

        if (world == null || pos == null)
            return;

        if (keyframe.lampDelayTicks() > 0) {
            PENDING.add(new Pending(tardis.getUuid(), world, pos.toImmutable(), keyframe));
            return;
        }

        flash(tardis.getUuid(), world, pos, keyframe);
    }

    private static void flash(UUID tardisId, ServerWorld world, BlockPos pos, ParticleKeyframe keyframe) {
        AitMoreConfig config = AitMoreConfigLoader.get();

        if (!config.lampFlashEnabled)
            return;

        if (config.lampFlashGroundLightEnabled && world.getBlockEntity(pos.up()) instanceof ExteriorLightBlockEntity light)
            light.flash(keyframe.lampFlashTicks());

        for (ServerPlayerEntity player : PlayerLookup.around(world, Vec3d.ofCenter(pos), BROADCAST_RADIUS)) {
            AitMoreNetworking.sendLampFlash(player, tardisId, pos, keyframe.lampFlashTicks(),
                    keyframe.lampHeight(), keyframe.haloSize());
        }
    }
}
