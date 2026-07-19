package dev.aitmore.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads config/ait-more.json once at startup. There's no hot-reload/reload command support -
 * a config change needs a server restart to take effect, same as most mod configs. If the file
 * doesn't exist yet (first run), the defaults from AitMoreConfig get written out so there's
 * something to edit; if it exists but fails to parse, defaults are used in-memory rather than
 * crashing the mod, and the broken file is left alone (untouched) rather than being silently
 * overwritten.
 */
public class AitMoreConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("ait-more");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ait-more.json");

    private static AitMoreConfig instance = new AitMoreConfig();

    public static AitMoreConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            writeDefault();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            AitMoreConfig loaded = GSON.fromJson(reader, AitMoreConfig.class);
            instance = loaded != null ? loaded : new AitMoreConfig();
            LOGGER.info("[ait-more] Loaded config from {}", CONFIG_PATH);
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            LOGGER.warn("[ait-more] Failed to load config/ait-more.json, using defaults for this session "
                    + "(the file itself was left untouched)", e);
            instance = new AitMoreConfig();
        }
    }

    private static void writeDefault() {
        instance = new AitMoreConfig();

        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }

            LOGGER.info("[ait-more] Wrote default config to {}", CONFIG_PATH);
        } catch (IOException e) {
            LOGGER.warn("[ait-more] Failed to write default config/ait-more.json - using in-memory defaults for this session", e);
        }
    }
}
