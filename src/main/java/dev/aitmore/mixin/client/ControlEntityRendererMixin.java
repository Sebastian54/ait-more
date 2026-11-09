package dev.aitmore.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.amble.ait.client.renderers.entities.ControlEntityRenderer;
import dev.amble.ait.core.entities.ConsoleControlEntity;

/**
 * Makes the native sequence hover marker (red-to-green progress icon, set up by
 * AdvancedModeManager via ConsoleControlEntity's tracked data) visible just by looking at the
 * control, instead of only while actively scanning it with a sonic screwdriver. AIT's own
 * renderLabelIfPresent only draws the marker when isScanningSonicInConsole(entity) AND
 * entity.isPartOfSequence() both hold - redirecting the sonic-scan check to always return true
 * leaves isPartOfSequence() as the only real gate, which is what both AIT's native random events
 * and Advanced Mode's combos actually want to signal passively.
 */
@Mixin(ControlEntityRenderer.class)
public abstract class ControlEntityRendererMixin {

    @Redirect(method = "renderLabelIfPresent(Ldev/amble/ait/core/entities/ConsoleControlEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/amble/ait/client/renderers/entities/ControlEntityRenderer;isScanningSonicInConsole(Ldev/amble/ait/core/entities/ConsoleControlEntity;)Z"),
            remap = false)
    private boolean aitmore$alwaysShowSequenceMarker(ConsoleControlEntity entity) {
        return true;
    }
}
