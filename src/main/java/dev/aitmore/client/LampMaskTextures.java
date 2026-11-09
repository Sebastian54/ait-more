package dev.aitmore.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import dev.aitmore.AitMore;
import dev.amble.ait.client.models.exteriors.ClassicExteriorModel;
import dev.amble.ait.client.models.exteriors.ExteriorModel;
import dev.amble.ait.client.models.exteriors.PoliceBoxCoralModel;
import dev.amble.ait.client.models.exteriors.PoliceBoxModel;

/**
 * Builds lamp-only copies of exterior glow textures at runtime, by keeping just the texture
 * regions the model's lamp cuboids are UV-mapped to. Works for any exterior on a known model
 * (AIT's built-ins included), with no per-exterior texture needed.
 */
public class LampMaskTextures {

    private static final Logger LOGGER = LoggerFactory.getLogger("ait-more");

    /** The models' texture size; lamp regions are scaled to the actual image size. */
    private static final int MODEL_TEXTURE_SIZE = 512;

    /** Box-UV regions {x0, y0, x1, y1} of each model's lamp cuboids, checked against AIT 1.2.13's textures. */
    private static final List<int[]> POLICE_BOX_LAMP = List.of(
            new int[] {17, 18, 33, 25}, new int[] {0, 18, 16, 29},
            new int[] {0, 315, 21, 327});
    private static final List<int[]> CORAL_LAMP = List.of(
            new int[] {14, 35, 28, 48}, new int[] {0, 57, 16, 71});
    private static final List<int[]> CLASSIC_LAMP = List.of(
            new int[] {0, 19, 16, 29}, new int[] {0, 43, 14, 46}, new int[] {0, 39, 14, 42});

    /** Empty Optional = this emission/model pair has no usable lamp mask. */
    private static final Map<String, Optional<Identifier>> CACHE = new HashMap<>();
    private static int nextId;

    @Nullable
    public static Identifier get(Identifier emission, ExteriorModel model) {
        List<int[]> regions = regionsFor(model);

        if (regions == null)
            return null;

        String key = emission + "|" + model.getClass().getName();
        return CACHE.computeIfAbsent(key, k -> build(emission, regions)).orElse(null);
    }

    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();

        for (Optional<Identifier> id : CACHE.values())
            id.ifPresent(client.getTextureManager()::destroyTexture);

        CACHE.clear();
    }

    @Nullable
    private static List<int[]> regionsFor(ExteriorModel model) {
        if (model instanceof PoliceBoxCoralModel)
            return CORAL_LAMP;
        if (model instanceof PoliceBoxModel)
            return POLICE_BOX_LAMP;
        if (model instanceof ClassicExteriorModel)
            return CLASSIC_LAMP;
        return null;
    }

    private static Optional<Identifier> build(Identifier emission, List<int[]> regions) {
        MinecraftClient client = MinecraftClient.getInstance();
        Optional<Resource> resource = client.getResourceManager().getResource(emission);

        if (resource.isEmpty())
            return Optional.empty();

        try (InputStream stream = resource.get().getInputStream(); NativeImage source = NativeImage.read(stream)) {
            int width = source.getWidth();
            int height = source.getHeight();
            float scale = width / (float) MODEL_TEXTURE_SIZE;
            NativeImage masked = new NativeImage(width, height, true);

            for (int[] region : regions) {
                int x1 = Math.min(width, Math.round(region[2] * scale));
                int y1 = Math.min(height, Math.round(region[3] * scale));

                for (int y = Math.round(region[1] * scale); y < y1; y++) {
                    for (int x = Math.round(region[0] * scale); x < x1; x++)
                        masked.setColor(x, y, source.getColor(x, y));
                }
            }

            Identifier id = Identifier.of(AitMore.MOD_ID, "lamp_mask/" + nextId++);
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(masked));
            return Optional.of(id);
        } catch (Exception e) {
            LOGGER.warn("[ait-more] Couldn't build a lamp-only glow texture from {}", emission, e);
            return Optional.empty();
        }
    }
}
