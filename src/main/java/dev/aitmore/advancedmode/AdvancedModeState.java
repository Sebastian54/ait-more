package dev.aitmore.advancedmode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether Advanced Mode is turned on for a given TARDIS (by uuid), persisted to
 * config/ait-more-advanced-mode.json so the toggle survives a server restart. Keyed by the
 * uuid's string form rather than java.util.UUID directly since Gson's default map-key handling
 * only writes a plain JSON object key for String/primitive/enum keys - anything else gets
 * serialized as an array of [key, value] pairs instead, which is needless complexity here.
 */
public class AdvancedModeState {

    private static final Logger LOGGER = LoggerFactory.getLogger("ait-more");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ait-more-advanced-mode.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();

    private static final Map<UUID, Boolean> ENABLED = new ConcurrentHashMap<>();

    public static void load() {
        ENABLED.clear();

        if (!Files.exists(PATH))
            return;

        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            Map<String, Boolean> raw = GSON.fromJson(reader, MAP_TYPE);

            if (raw == null)
                return;

            raw.forEach((key, value) -> {
                try {
                    ENABLED.put(UUID.fromString(key), value);
                } catch (IllegalArgumentException ignored) {
                    // Skip a corrupted entry rather than failing the whole load.
                }
            });

            LOGGER.info("[ait-more] Loaded Advanced Mode state from {}", PATH);
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            LOGGER.warn("[ait-more] Failed to load {}, Advanced Mode will start disabled for every TARDIS this session", PATH, e);
        }
    }

    public static boolean isEnabled(UUID tardisId) {
        return ENABLED.getOrDefault(tardisId, false);
    }

    public static void setEnabled(UUID tardisId, boolean enabled) {
        ENABLED.put(tardisId, enabled);
        save();
    }

    private static void save() {
        Map<String, Boolean> raw = new HashMap<>();
        ENABLED.forEach((uuid, enabled) -> raw.put(uuid.toString(), enabled));

        try {
            Files.createDirectories(PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(raw, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[ait-more] Failed to save {} - the toggle will still work this session but won't survive a restart", PATH, e);
        }
    }
}
