package dev.aitmore.particlefx;

import java.util.Set;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import dev.aitmore.AitMoreNetworking;
import dev.aitmore.config.AitMoreConfig;
import dev.aitmore.config.AitMoreConfigLoader;
import dev.amble.ait.core.blocks.types.HorizontalDirectionalBlock;
import dev.amble.ait.core.tardis.ServerTardis;

/**
 * Fires a keyframe's particle (at every console in the interior), screen shake, and knockback.
 * Shake/knockback are computed once per player per keyframe, not once per console - only the
 * particle is meant to appear at every console; shaking a player's camera or shoving them
 * several times over for one event, just because a TARDIS happens to have multiple consoles,
 * would be wrong.
 */
public class ParticleKeyframeFx {

    /** Roughly matches vanilla explosion/TNT knockback's vertical lift proportion. */
    private static final double KNOCKBACK_VERTICAL_LIFT = 0.3;

    public static void fire(ServerTardis tardis, ParticleKeyframe keyframe) {
        AitMoreConfig config = AitMoreConfigLoader.get();
        ServerWorld world = tardis.world();
        Set<BlockPos> consolePositions = tardis.getDesktop().getConsolePos();

        if (config.particlesEnabled && keyframe.particle() != null) {
            for (BlockPos consolePos : consolePositions) {
                Vec3d offset = rotateForFacing(facingOf(world.getBlockState(consolePos)), keyframe.offset());
                Vec3d pos = Vec3d.ofCenter(consolePos).add(offset);

                world.spawnParticles(keyframe.particle(), pos.x, pos.y, pos.z, keyframe.count(),
                        keyframe.spread().x, keyframe.spread().y, keyframe.spread().z, keyframe.speed());
            }
        }

        boolean wantsShake = config.shakeEnabled && keyframe.shake() > 0F;
        boolean wantsKnockback = config.knockbackEnabled && keyframe.knockback() > 0F;
        boolean wantsVision = config.visionEnabled && keyframe.visionDistance() >= 0F;

        if (!wantsShake && !wantsKnockback && !wantsVision)
            return;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (wantsShake) {
                float intensity = keyframe.shake() * config.shakeIntensityMultiplier;
                AitMoreNetworking.sendShake(player, intensity, keyframe.shakeDurationTicks());
            }

            if (wantsKnockback) {
                float strength = Math.min(keyframe.knockback() * config.knockbackStrengthMultiplier, config.maxKnockbackStrength);
                applyKnockback(player, nearestConsole(consolePositions, player.getPos()), strength);
            }

            if (wantsVision)
                AitMoreNetworking.sendVision(player, keyframe.visionDistance(), keyframe.visionDurationTicks(), keyframe.visionRampTicks());
        }
    }

    /**
     * Stops any in-progress continuous shake for everyone in the interior - see
     * AitMoreNetworking#sendCancelShake for why this exists and when it gets called.
     */
    public static void cancelShakeForAll(ServerTardis tardis) {
        for (ServerPlayerEntity player : tardis.world().getPlayers())
            AitMoreNetworking.sendCancelShake(player);
    }

    /**
     * Stops any in-progress persistent vision override for everyone in the interior - see
     * AitMoreNetworking#sendClearVision for why this exists and when it gets called.
     */
    public static void cancelVisionForAll(ServerTardis tardis) {
        int fadeTicks = Math.round(AitMoreConfigLoader.get().visionResetFadeSeconds * 20F);

        for (ServerPlayerEntity player : tardis.world().getPlayers())
            AitMoreNetworking.sendClearVision(player, fadeTicks);
    }

    private static void applyKnockback(ServerPlayerEntity player, BlockPos consolePos, float strength) {
        if (consolePos == null)
            return;

        Vec3d diff = player.getPos().subtract(Vec3d.ofCenter(consolePos));
        Vec3d horizontal = new Vec3d(diff.x, 0, diff.z);

        // Player is standing right on top of the console (or very close) - pick an arbitrary
        // push direction rather than dividing by a near-zero length.
        Vec3d direction = horizontal.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : horizontal.normalize();

        player.addVelocity(direction.x * strength, KNOCKBACK_VERTICAL_LIFT * strength, direction.z * strength);
        player.velocityModified = true;
    }

    private static BlockPos nearestConsole(Set<BlockPos> consolePositions, Vec3d playerPos) {
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : consolePositions) {
            double distance = Vec3d.ofCenter(pos).squaredDistanceTo(playerPos);

            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = pos;
            }
        }

        return nearest;
    }

    private static Direction facingOf(BlockState state) {
        if (!state.contains(HorizontalDirectionalBlock.FACING))
            return Direction.NORTH;

        return state.get(HorizontalDirectionalBlock.FACING);
    }

    private static Vec3d rotateForFacing(Direction facing, Vec3d offset) {
        return switch (facing) {
            case SOUTH -> new Vec3d(-offset.x, offset.y, -offset.z);
            case WEST -> new Vec3d(-offset.z, offset.y, offset.x);
            case EAST -> new Vec3d(offset.z, offset.y, -offset.x);
            default -> offset;
        };
    }
}
