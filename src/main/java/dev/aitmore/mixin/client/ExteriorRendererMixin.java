package dev.aitmore.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import dev.aitmore.client.LampFlashClient;
import dev.amble.ait.client.models.exteriors.ExteriorModel;
import dev.amble.ait.client.renderers.AITRenderLayers;
import dev.amble.ait.client.renderers.exteriors.ExteriorRenderer;
import dev.amble.ait.client.tardis.ClientTardis;
import dev.amble.ait.core.blockentities.ExteriorBlockEntity;
import dev.amble.ait.data.schema.exterior.ClientExteriorVariantSchema;

/**
 * Re-draws the exterior's glow at full brightness during a keyframe lamp flash. AIT's own glow
 * pass is dimmed by the exterior's alpha and skipped entirely below ~10% alpha, so mid-mat the
 * lamp would otherwise barely show. Injected at the "biome" profiler swap, which comes right after
 * AIT's emission pass while the model's transforms are still on the matrix stack.
 */
@Mixin(value = ExteriorRenderer.class, remap = false)
public abstract class ExteriorRendererMixin {

    @Shadow
    private ClientExteriorVariantSchema variant;

    @Shadow
    private ExteriorModel model;

    @Inject(method = "renderExterior",
            at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
                    args = "ldc=biome", remap = true))
    private void aitmore$renderLampFlash(Profiler profiler, ClientTardis tardis, ExteriorBlockEntity entity, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, CallbackInfo ci) {
        if (tardis == null || this.variant == null || this.model == null)
            return;

        float intensity = LampFlashClient.getIntensity(tardis.getUuid(), tickDelta);

        if (intensity <= 0F)
            return;

        Identifier texture = LampFlashClient.resolveFlashTexture(this.variant.emission(), this.model);

        if (texture == null)
            return;

        this.model.renderWithAnimations(tardis, entity, this.model.getPart(), matrices,
                vertexConsumers.getBuffer(AITRenderLayers.tardisEmissiveCullZOffset(texture, true)),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, intensity, tickDelta);
    }
}
