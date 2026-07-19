package dev.aitmore.particlefx;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import dev.aitmore.AitMore;

/**
 * Loads {@code data/<namespace>/fx/animation/particle_keyframes/*.json} files on every
 * datapack (re)load. Each file maps a demat/mat animation id to a list of "spawn this particle
 * at this time" events; see AnimatedTravelHandlerMixin for where those get fired.
 *
 * File shape:
 * <pre>{@code
 * {
 *   "animation": "ait-more:flatline_mat",
 *   "keyframes": [
 *     {
 *       "time": 3.5,
 *       "particle": "minecraft:cloud", "offset": [0, 1.2, 0], "count": 10, "spread": [0.2, 0.2, 0.2], "speed": 0.05,
 *       "shake": 0.5,
 *       "shake_duration": 2.0,
 *       "knockback": 1.0,
 *       "vision": 3,
 *       "vision_duration": 4.0,
 *       "vision_ramp": 1.5
 *     }
 *   ]
 * }
 * }</pre>
 * "particle" (and everything under it) is optional - a keyframe can be shake/knockback/vision-only.
 * "shake" and "knockback" both default to 0 (off) when omitted. "shake_duration" (seconds,
 * default 0) makes the shake continuous - the client keeps re-applying it every tick for that
 * long instead of a single instant jolt - and is ignored if "shake" isn't set. Only simple
 * (parameterless) particle types are supported - anything needing extra data (dust color,
 * block state, item stack, ...) isn't handled by this loader. "vision" (blocks of visibility,
 * omitted = don't touch vision this keyframe) replicates AIT's own power-off blindness fog on
 * demand; "vision_duration" (seconds, default 0 = persists until the animation ends) makes it a
 * temporary pulse instead; "vision_ramp" (seconds, default 0 = instant snap) eases into it
 * gradually instead of jumping straight to full blindness - see ParticleKeyframe for the full
 * behaviour.
 */
public class ParticleKeyframeLoader implements SimpleSynchronousResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("ait-more");
    private static final Identifier ID = Identifier.of(AitMore.MOD_ID, "particle_keyframes");
    private static final String RESOURCE_PATH = "fx/animation/particle_keyframes";

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new ParticleKeyframeLoader());
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, List<ParticleKeyframe>> loaded = new HashMap<>();

        for (Map.Entry<Identifier, Resource> entry : manager.findResources(RESOURCE_PATH, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                parseFile(JsonParser.parseReader(reader).getAsJsonObject(), loaded);
            } catch (Exception e) {
                LOGGER.warn("[ait-more] Failed to load particle keyframe file {}", entry.getKey(), e);
            }
        }

        ParticleKeyframeRegistry.set(loaded);
        LOGGER.info("[ait-more] Loaded particle keyframes for {} animation(s)", loaded.size());
    }

    private void parseFile(JsonObject json, Map<Identifier, List<ParticleKeyframe>> loaded) {
        Identifier animationId = new Identifier(json.get("animation").getAsString());
        List<ParticleKeyframe> keyframes = new ArrayList<>();

        for (JsonElement element : json.getAsJsonArray("keyframes")) {
            ParticleKeyframe keyframe = parseKeyframe(element.getAsJsonObject(), animationId);

            if (keyframe != null)
                keyframes.add(keyframe);
        }

        keyframes.sort(Comparator.comparingInt(ParticleKeyframe::tick));
        loaded.put(animationId, keyframes);
    }

    private ParticleKeyframe parseKeyframe(JsonObject json, Identifier animationId) {
        ParticleEffect particle = null;
        Vec3d offset = Vec3d.ZERO;
        Vec3d spread = Vec3d.ZERO;
        int count = 1;
        double speed = 0.0;

        if (json.has("particle")) {
            Identifier particleId = new Identifier(json.get("particle").getAsString());
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(particleId);

            if (!(type instanceof ParticleEffect resolved)) {
                LOGGER.warn("[ait-more] Skipping particle on keyframe for {}: '{}' is not a simple particle type "
                        + "(parameterized types like dust or block state aren't supported yet)", animationId, particleId);
            } else {
                particle = resolved;
                offset = readVec3d(json, "offset", Vec3d.ZERO);
                spread = readVec3d(json, "spread", Vec3d.ZERO);
                count = json.has("count") ? json.get("count").getAsInt() : 1;
                speed = json.has("speed") ? json.get("speed").getAsDouble() : 0.0;
            }
        }

        float shake = json.has("shake") ? json.get("shake").getAsFloat() : 0F;
        int shakeDurationTicks = (shake > 0F && json.has("shake_duration"))
                ? Math.round(json.get("shake_duration").getAsFloat() * 20F)
                : 0;
        float knockback = json.has("knockback") ? json.get("knockback").getAsFloat() : 0F;

        float visionDistance = json.has("vision") ? json.get("vision").getAsFloat() : -1F;
        int visionDurationTicks = (visionDistance >= 0F && json.has("vision_duration"))
                ? Math.round(json.get("vision_duration").getAsFloat() * 20F)
                : 0;
        int visionRampTicks = (visionDistance >= 0F && json.has("vision_ramp"))
                ? Math.round(json.get("vision_ramp").getAsFloat() * 20F)
                : 0;

        if (particle == null && shake <= 0F && knockback <= 0F && visionDistance < 0F) {
            LOGGER.warn("[ait-more] Skipping empty keyframe (no particle/shake/knockback/vision) for {}", animationId);
            return null;
        }

        int tick = Math.round(json.get("time").getAsFloat() * 20F);

        return new ParticleKeyframe(tick, particle, offset, count, spread, speed, shake, shakeDurationTicks, knockback,
                visionDistance, visionDurationTicks, visionRampTicks);
    }

    private static Vec3d readVec3d(JsonObject json, String key, Vec3d fallback) {
        if (!json.has(key))
            return fallback;

        var array = json.getAsJsonArray(key);
        return new Vec3d(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }
}
