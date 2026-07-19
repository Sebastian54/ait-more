package dev.aitmore.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;

import dev.aitmore.client.AitMoreClient;

/**
 * Client-only fog override for keyframe-triggered "vision" events (see ParticleKeyframeFx),
 * replicating the look of AIT's own power-off blindness fog (dev.amble.ait.mixin.client.rendering
 * .BGRendererMixin -> dev.amble.ait.client.util.FoggyUtils#overrideFog) but driven by our own
 * client state instead of tardis.fuel().hasPower().
 *
 * Priority is raised above the default (1000) so this mixin gets applied - and therefore its
 * TAIL injection executes - after AIT's own applyFog hook for the same tick, letting an active
 * keyframe vision override win over AIT's power-based fog rather than being clobbered by it.
 * Mixin execution order between two different mods' injectors at the same target/point isn't
 * strictly guaranteed by anything other than this priority ordering, but it's the same mechanism
 * AIT itself relies on for its own fog override.
 */
@Mixin(value = BackgroundRenderer.class, priority = 2000)
public class BackgroundRendererMixin {

    @Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/BackgroundRenderer$FogType;FZF)V",
            at = @At("TAIL"))
    private static void aitmore$overrideFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
            boolean thickFog, float tickDelta, CallbackInfo ci) {
        float distance = AitMoreClient.getActiveVisionDistance();

        if (distance < 0F)
            return;

        float end = Math.max(2F, distance);
        float start = Math.max(0F, end - 4F);

        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        RenderSystem.setShaderFogColor(0F, 0F, 0F, 1F);
    }
}
