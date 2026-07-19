package dev.aitmore.light;

import java.util.UUID;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.aitmore.config.AitMoreConfigLoader;
import dev.amble.ait.core.AITBlocks;
import dev.amble.ait.core.tardis.ServerTardis;
import dev.amble.ait.core.tardis.Tardis;
import dev.amble.ait.core.tardis.manager.ServerTardisManager;

/**
 * Tracks the TARDIS this light is welded to (by UUID, since this block lives wherever the
 * exterior is - the overworld, typically - not inside the TARDIS's own interior dimension, so
 * it can't rely on a TardisServerWorld link the way interior blocks do).
 *
 * Every tick: if the exterior directly below is gone (broken by a player, removed by a demat,
 * or anything else), this block removes itself - this is the ONLY place removal is handled,
 * deliberately, so it's correct regardless of *how* the exterior went away rather than trying
 * to hook every possible removal path individually. Otherwise its light level tracks
 * getAlpha() (how "materialized" the TARDIS currently is), forced to 0 outright whenever the
 * TARDIS has no power (the same hasPower() check TravelHandler itself already uses to decide
 * the exterior block's own light level on placement), and its ALARM state tracks whether the
 * TARDIS's alarms are currently on.
 */
public class ExteriorLightBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger("ait-more");
    private static final int MAX_LEVEL = 15;

    /** Logged once ever, not once per tick, since this can otherwise repeat 20x/second for as long as the handbrake stays engaged. */
    private static boolean loggedHandbrakeAlphaBug = false;

    private UUID tardisId;

    public ExteriorLightBlockEntity(BlockPos pos, BlockState state) {
        super(AitMoreLightBlocks.EXTERIOR_LIGHT_ENTITY, pos, state);
    }

    public void setTardisId(UUID tardisId) {
        this.tardisId = tardisId;
        this.markDirty();
    }

    public void serverTick(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos.down()).isOf(AITBlocks.EXTERIOR_BLOCK) || !AitMoreConfigLoader.get().exteriorLightEnabled) {
            world.removeBlock(pos, false);
            return;
        }

        if (this.tardisId == null)
            return;

        MinecraftServer server = world.getServer();
        ServerTardis tardis = ServerTardisManager.getInstance().demandTardis(server, this.tardisId);

        if (tardis == null)
            return;

        int level = resolveLevel(tardis);
        boolean alarm = tardis.alarm().isEnabled();

        BlockState state = this.getCachedState();

        if (state.get(ExteriorLightBlock.LEVEL) != level || state.get(ExteriorLightBlock.ALARM) != alarm) {
            world.setBlockState(pos, state.with(ExteriorLightBlock.LEVEL, level).with(ExteriorLightBlock.ALARM, alarm));
        }
    }

    private static int resolveLevel(Tardis tardis) {
        if (!tardis.fuel().hasPower())
            return 0;

        float alpha;

        try {
            alpha = tardis.travel().getAlpha();
        } catch (ClassCastException e) {
            // AIT's own AnimationHolder#getHandbrakeAlpha unconditionally calls Tardis#asClient()
            // internally, which throws exactly this when called on a ServerTardis - a latent bug
            // in AIT itself, reachable specifically while the handbrake is engaged (e.g. during
            // takeoff). Nothing else in AIT calls getAlpha() from server-side code, so this never
            // surfaced before this block started doing it every tick. Can't fix AIT's source, so
            // fall back to full brightness rather than let one bad tick crash the whole server.
            if (!loggedHandbrakeAlphaBug) {
                loggedHandbrakeAlphaBug = true;
                LOGGER.warn("[ait-more] Tardis#travel().getAlpha() threw ClassCastException (AIT's own "
                        + "handbrake-alpha bug, calling Tardis#asClient() server-side) - falling back to "
                        + "full brightness for the exterior light whenever this happens. Logged once only.", e);
            }

            return MAX_LEVEL;
        }

        return Math.round(Math.max(0F, Math.min(1F, alpha)) * MAX_LEVEL);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        if (this.tardisId != null)
            nbt.putUuid("TardisId", this.tardisId);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        if (nbt.containsUuid("TardisId"))
            this.tardisId = nbt.getUuid("TardisId");
    }
}
