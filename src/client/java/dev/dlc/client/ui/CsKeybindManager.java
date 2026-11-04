package dev.dlc.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.dlc.client.module.Module;
import dev.dlc.client.module.ModuleManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsKeybindManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/Keybinds");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final CsKeybindManager INSTANCE = new CsKeybindManager();

    private final Map<String, Integer> bindings = new HashMap<>();
    private final Map<Integer, Boolean> previousKeyStates = new HashMap<>();
    private final Path configFile =
            FabricLoader.getInstance().getConfigDir().resolve("moduledlc/binds.json");

    public static CsKeybindManager get() {
        return INSTANCE;
    }

    private CsKeybindManager() {
        load();
    }

    public int binding(String id) {
        return bindings.getOrDefault(id, GLFW.GLFW_KEY_UNKNOWN);
    }

    public String label(String id) {
        int k = binding(id);
        if (k == GLFW.GLFW_KEY_UNKNOWN) return "";
        String knownName =
                switch (k) {
                    case GLFW.GLFW_KEY_ESCAPE -> "ESC";
                    case GLFW.GLFW_KEY_SPACE -> "SPACE";
                    case GLFW.GLFW_KEY_ENTER -> "ENTER";
                    case GLFW.GLFW_KEY_TAB -> "TAB";
                    case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
                    case GLFW.GLFW_KEY_DELETE -> "DELETE";
                    case GLFW.GLFW_KEY_INSERT -> "INSERT";
                    case GLFW.GLFW_KEY_HOME -> "HOME";
                    case GLFW.GLFW_KEY_END -> "END";
                    case GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP";
                    case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
                    case GLFW.GLFW_KEY_LEFT -> "LEFT";
                    case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
                    case GLFW.GLFW_KEY_UP -> "UP";
                    case GLFW.GLFW_KEY_DOWN -> "DOWN";
                    case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
                    case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT";
                    case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL";
                    case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-CTRL";
                    case GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT";
                    case GLFW.GLFW_KEY_RIGHT_ALT -> "R-ALT";
                    case GLFW.GLFW_KEY_LEFT_SUPER -> "L-SUPER";
                    case GLFW.GLFW_KEY_RIGHT_SUPER -> "R-SUPER";
                    default -> null;
                };
        if (knownName != null) return knownName;
        if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F25) {
            return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
        }
        String n = GLFW.glfwGetKeyName(k, 0);
        return n != null ? n.toUpperCase(Locale.ROOT) : "KEY" + k;
    }

    public void bind(String id, int key) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Binding id must not be blank");
        }
        if (key != GLFW.GLFW_KEY_UNKNOWN
                && (key < GLFW.GLFW_KEY_SPACE || key > GLFW.GLFW_KEY_LAST)) {
            throw new IllegalArgumentException("Invalid keyboard key: " + key);
        }

        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            if (bindings.remove(id) == null) return;
        } else {
            Integer oldKey = bindings.get(id);
            if (oldKey != null && oldKey == key) return;
            bindings.put(id, key);
        }
        save();
    }

    public void tick(Minecraft minecraft, ModuleManager modules) {
        var currentKeys = new HashSet<>(bindings.values());
        var allModules = modules.all();
        previousKeyStates.keySet().removeIf(key -> !currentKeys.contains(key));

        long window = minecraft.getWindow().handle();
        for (int key : currentKeys) {
            boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            boolean wasDown = previousKeyStates.getOrDefault(key, false);
            previousKeyStates.put(key, down);
            if (!down || wasDown || minecraft.screen != null) continue;

            for (Module module : allModules) {
                if (binding(module.id()) == key) {
                    module.setEnabled(!module.enabled());
                }
            }
        }
    }

    private void load() {
        if (!Files.exists(configFile)) return;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(configFile));
            if (!root.isJsonObject()) {
                LOGGER.error("Ignoring keybind config {}: expected a JSON object", configFile);
                return;
            }
            JsonObject entries = root.getAsJsonObject();
            for (var entry : entries.entrySet()) {
                JsonElement value = entry.getValue();
                if (entry.getKey().isBlank()
                        || !value.isJsonPrimitive()
                        || !value.getAsJsonPrimitive().isNumber()) {
                    LOGGER.warn("Ignoring invalid keybind for module '{}'", entry.getKey());
                    continue;
                }
                double numericKey = value.getAsDouble();
                if (!Double.isFinite(numericKey)
                        || numericKey != Math.rint(numericKey)
                        || numericKey < GLFW.GLFW_KEY_SPACE
                        || numericKey > GLFW.GLFW_KEY_LAST) {
                    LOGGER.warn(
                            "Ignoring invalid key code {} for module '{}'",
                            numericKey,
                            entry.getKey());
                    continue;
                }
                bindings.put(entry.getKey(), (int) numericKey);
            }
        } catch (IOException | JsonParseException exception) {
            LOGGER.error("Could not read keybind config {}", configFile, exception);
        }
    }

    private void save() {
        JsonObject entries = new JsonObject();
        bindings.forEach(entries::addProperty);
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(entries));
        } catch (IOException exception) {
            LOGGER.error("Could not save keybind config {}", configFile, exception);
        }
    }
}
