package dev.aitmore.mixin;

import dev.aitmore.AitMore;
import dev.aitmore.config.AitMoreConfigLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    private static final Logger AITMORE_LOGGER = LoggerFactory.getLogger("ait-more");

    private static final Identifier JELLY_BABIES_ID = Identifier.of("ait-extras", "jelly_babies");

    @Inject(method = "dropSelectedItem", at = @At("HEAD"))
    private void aitmore$onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        ItemStack stack = player.getInventory().getMainHandStack();
        if (stack.isEmpty()) {
            return;
        }

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        AITMORE_LOGGER.info("[ait-more debug] Player-dropped item id: {}", itemId);

        if (itemId.equals(JELLY_BABIES_ID) && AitMoreConfigLoader.get().jellyBabySoundEnabled) {
            AITMORE_LOGGER.info("[ait-more debug] Match! Playing sound.");
            player.getWorld().playSound(
                    null,
                    player.getBlockPos(),
                    AitMore.JELLY_BABIES_DROP_SOUND,
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.0F
            );
        }
    }
}