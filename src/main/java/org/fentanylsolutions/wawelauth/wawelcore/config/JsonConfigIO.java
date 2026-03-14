package org.fentanylsolutions.wawelauth.wawelcore.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.fentanylsolutions.wawelauth.WawelAuth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Loads and saves JSON config files using GSON (bundled with Minecraft).
 * <p>
 * On load: if the file exists, deserializes it over a fresh default instance,
 * so missing fields keep their defaults. If the file doesn't exist or is
 * malformed, creates one with all defaults and returns that.
 * <p>
 * On save: serializes with pretty printing so users can hand-edit the file.
 * All IO uses UTF-8 encoding explicitly.
 */
public class JsonConfigIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    /**
     * Load a config from a JSON file, or create a default one if it doesn't exist.
     *
     * @param file the JSON file to load from
     * @param type the config class (must have a no-arg constructor with defaults)
     * @return the loaded or default config instance
     */
    public static <T> T load(File file, Class<T> type) {
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                T config = GSON.fromJson(reader, type);
                if (config == null) {
                    config = createDefault(type);
                }
                // Re-save to pick up any new fields added in code updates
                save(file, config);
                return config;
            } catch (JsonSyntaxException e) {
                throw new RuntimeException(
                    "Malformed JSON in " + file.getAbsolutePath()
                        + ": fix the syntax error or delete the file to regenerate defaults. "
                        + "Error: "
                        + e.getMessage(),
                    e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read config file " + file.getAbsolutePath(), e);
            }
        }

        T config = createDefault(type);
        save(file, config);
        return config;
    }

    /**
     * Save a config to a JSON file with pretty printing (UTF-8).
     */
    public static <T> void save(File file, T config) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            WawelAuth.LOG.error("Failed to create config directory: {}", parent);
            return;
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            WawelAuth.LOG.error("Failed to save config {}", file.getName(), e);
        }
    }

    private static <T> T createDefault(Class<T> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Config class must have a public no-arg constructor: " + type.getName(), e);
        }
    }
}
