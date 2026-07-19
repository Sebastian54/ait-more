package dev.aitmore.light;

import java.util.UUID;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import dev.aitmore.config.AitMoreConfigLoader;

/**
 * Places (or refreshes) the light block directly above a freshly-placed exterior. Called from
 * TravelHandlerMixin every time an exterior is placed - which covers brand new TARDISes and
 * every subsequent landing uniformly, since both paths funnel through the same private
 * TravelHandler#placeExterior method - so this doubles as the lazy backfill for TARDISes that
 * existed before this feature did: they simply get welded the next time they land, rather than
 * every TARDIS on the server needing to be scanned and fixed up at once on startup.
 */
public class ExteriorLightWelder {

    public static void weld(ServerWorld world, BlockPos exteriorPos, UUID tardisId) {
        if (!AitMoreConfigLoader.get().exteriorLightEnabled)
            return;

        BlockPos lightPos = exteriorPos.up();
        BlockState existing = world.getBlockState(lightPos);

        // Don't overwrite whatever a player actually built above their TARDIS - only place
        // into empty/replaceable space, or refresh an already-existing light of our own.
        if (!existing.isOf(AitMoreLightBlocks.EXTERIOR_LIGHT) && !existing.isReplaceable())
            return;

        world.setBlockState(lightPos, AitMoreLightBlocks.EXTERIOR_LIGHT.getDefaultState());

        if (world.getBlockEntity(lightPos) instanceof ExteriorLightBlockEntity light)
            light.setTardisId(tardisId);
    }
}
