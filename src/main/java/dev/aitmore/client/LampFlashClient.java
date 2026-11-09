package dev.aitmore.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import dev.aitmore.AitMore;
import dev.aitmore.AitMoreNetworking;
import dev.amble.ait.client.models.exteriors.ExteriorModel;
import dev.amble.ait.data.datapack.DatapackConsole;

/**
 * Client half of the keyframe "lamp_flash": tracks active flashes per TARDIS, draws the glow
 * halo in world space, and picks the texture ExteriorRendererMixin re-draws at full brightness.
 */
public class LampFlashClient {

    private static final Identifier HALO_TEXTURE = Identifier.of(AitMore.MOD_ID, "textures/fx/lamp_halo.png");

    private record Flash(BlockPos pos, int totalTicks, int remainingTicks, float lampHeight, float haloSize) {
    }

    private static final Map<UUID, Flash> FLASHES = new HashMap<>();
    private static final Map<Identifier, Optional<Identifier>> LAMP_OVERRIDE_CACHE = new HashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(AitMoreNetworking.LAMP_FLASH_PACKET, (client, handler, buf, responseSender) -> {
            UUID tardisId = buf.readUuid();
            BlockPos pos = buf.readBlockPos();
            int durationTicks = buf.readVarInt();
            float lampHeight = buf.readFloat();
            float haloSize = buf.readFloat();

            client.execute(() -> FLASHES.put(tardisId, new Flash(pos, durationTicks, durationTicks, lampHeight, haloSize)));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Iterator<Map.Entry<UUID, Flash>> iterator = FLASHES.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, Flash> entry = iterator.next();
                Flash flash = entry.getValue();

                if (flash.remainingTicks() <= 1) {
                    iterator.remove();
                } else {
                    entry.setValue(new Flash(flash.pos(), flash.totalTicks(), flash.remainingTicks() - 1,
                            flash.lampHeight(), flash.haloSize()));
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FLASHES::clear));

        WorldRenderEvents.AFTER_ENTITIES.register(LampFlashClient::renderHalos);

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(AitMore.MOD_ID, "lamp_flash_textures");
            }

            @Override
            public void reload(ResourceManager manager) {
                LAMP_OVERRIDE_CACHE.clear();
                LampMaskTextures.clear();
            }
        });
    }

    /** 0 when no flash is active for this TARDIS, up to 1 at full brightness: fading in over the first half and out over the second, with no hold. */
    public static float getIntensity(UUID tardisId, float tickDelta) {
        Flash flash = FLASHES.get(tardisId);

        if (flash == null)
            return 0F;

        float progress = (flash.totalTicks() - flash.remainingTicks() + tickDelta) / flash.totalTicks();
        return Math.max(0F, Math.min(1F, Math.min(progress, 1F - progress) * 2F));
    }

    /**
     * A lamp-only glow texture: the exterior's own {@code <emission>_lamp.png} if it has one,
     * otherwise one masked from its emission texture using the model's lamp UVs. Null when neither
     * is possible (an unknown model), in which case only the halo flashes.
     */
    @Nullable
    public static Identifier resolveFlashTexture(@Nullable Identifier emission, ExteriorModel model) {
        if (emission == null || emission.equals(DatapackConsole.EMPTY))
            return null;

        Optional<Identifier> override = LAMP_OVERRIDE_CACHE.computeIfAbsent(emission, id -> {
            String path = id.getPath();

            if (!path.endsWith(".png"))
                return Optional.empty();

            Identifier lamp = Identifier.of(id.getNamespace(), path.substring(0, path.length() - 4) + "_lamp.png");
            return MinecraftClient.getInstance().getResourceManager().getResource(lamp).isPresent()
                    ? Optional.of(lamp)
                    : Optional.empty();
        });

        return override.orElseGet(() -> LampMaskTextures.get(emission, model));
    }

    private static void renderHalos(WorldRenderContext context) {
        if (FLASHES.isEmpty())
            return;

        VertexConsumerProvider consumers = context.consumers();

        if (consumers == null)
            return;

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();
        RenderLayer layer = RenderLayer.getEyes(HALO_TEXTURE);
        VertexConsumer buffer = consumers.getBuffer(layer);

        for (Map.Entry<UUID, Flash> entry : FLASHES.entrySet()) {
            Flash flash = entry.getValue();
            float intensity = getIntensity(entry.getKey(), context.tickDelta());

            if (intensity <= 0F)
                continue;

            matrices.push();
            matrices.translate(flash.pos().getX() + 0.5 - cameraPos.x, flash.pos().getY() + flash.lampHeight() - cameraPos.y,
                    flash.pos().getZ() + 0.5 - cameraPos.z);
            matrices.multiply(camera.getRotation());

            float half = flash.haloSize() * (0.6F + 0.4F * intensity) / 2F;
            int color = Math.round(255 * intensity);
            Matrix4f position = matrices.peek().getPositionMatrix();
            Matrix3f normal = matrices.peek().getNormalMatrix();

            // Both windings, so the halo shows regardless of the layer's face culling.
            vertex(buffer, position, normal, -half, -half, 0F, 1F, color);
            vertex(buffer, position, normal, half, -half, 1F, 1F, color);
            vertex(buffer, position, normal, half, half, 1F, 0F, color);
            vertex(buffer, position, normal, -half, half, 0F, 0F, color);

            vertex(buffer, position, normal, -half, half, 0F, 0F, color);
            vertex(buffer, position, normal, half, half, 1F, 0F, color);
            vertex(buffer, position, normal, half, -half, 1F, 1F, color);
            vertex(buffer, position, normal, -half, -half, 0F, 1F, color);

            matrices.pop();
        }

        if (consumers instanceof VertexConsumerProvider.Immediate immediate)
            immediate.draw(layer);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x, float y, float u, float v,
            int color) {
        buffer.vertex(position, x, y, 0F)
                .color(color, color, color, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normal, 0F, 1F, 0F)
                .next();
    }
}
