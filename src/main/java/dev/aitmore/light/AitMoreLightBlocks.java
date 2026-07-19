package dev.aitmore.light;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import dev.aitmore.AitMore;

public class AitMoreLightBlocks {

    public static final Block EXTERIOR_LIGHT = new ExteriorLightBlock(AbstractBlock.Settings.create()
            .noCollision()
            .nonOpaque()
            .strength(-1.0F, 3600000.0F)
            .luminance(state -> state.get(ExteriorLightBlock.LEVEL)));

    public static final BlockEntityType<ExteriorLightBlockEntity> EXTERIOR_LIGHT_ENTITY =
            FabricBlockEntityTypeBuilder.create(ExteriorLightBlockEntity::new, EXTERIOR_LIGHT).build();

    public static void register() {
        Registry.register(Registries.BLOCK, Identifier.of(AitMore.MOD_ID, "exterior_light"), EXTERIOR_LIGHT);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(AitMore.MOD_ID, "exterior_light"), EXTERIOR_LIGHT_ENTITY);
    }
}
