package dev.dlc.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.dlc.client.module.Module;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HudLayoutStore {
    private record Position(float x, float y) {}

    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/HUD");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("moduledlc/hud-layouts.json");
    private static final Map<String, Map<String, Position>> POSITIONS = load();

    private HudLayoutStore() {}

    public static synchronized void restore(Module module) {
        Map<String, Position> saved = POSITIONS.get(module.id());
        if (saved == null) return;

        module.hudElements().stream()
                .sorted(Comparator.comparingInt(HudElement::depth))
                .forEach(
                        element -> {
                            Position position = saved.get(element.id());
                            if (position != null) {
                                element.setPosition(position.x(), position.y());
                            }
                        });
    }

    public static synchronized void saveModules(Collection<Module> modules) {
        for (Module module : modules) {
            Map<String, Position> saved =
                    POSITIONS.computeIfAbsent(module.id(), ignored -> new LinkedHashMap<>());
            for (HudElement element : module.hudElements()) {
                saved.put(element.id(), new Position(element.x(), element.y()));
            }
        }
        write();
    }

    private static Map<String, Map<String, Position>> load() {
        Map<String, Map<String, Position>> result = new LinkedHashMap<>();
        if (!Files.exists(CONFIG_FILE)) return result;

        try {
            JsonElement root = JsonParser.parseString(Files.readString(CONFIG_FILE));
            if (!root.isJsonObject()) {
                LOGGER.error("Ignoring HUD layout file {}: expected a JSON object", CONFIG_FILE);
                return result;
            }
            for (var moduleEntry : root.getAsJsonObject().entrySet()) {
                if (!moduleEntry.getValue().isJsonObject()) continue;
                Map<String, Position> modulePositions = new LinkedHashMap<>();
                for (var elementEntry : moduleEntry.getValue().getAsJsonObject().entrySet()) {
                    if (!elementEntry.getValue().isJsonObject()) continue;
                    JsonObject coordinates = elementEntry.getValue().getAsJsonObject();
                    if (!coordinates.has("x") || !coordinates.has("y")) continue;
                    double x = coordinates.get("x").getAsDouble();
                    double y = coordinates.get("y").getAsDouble();
                    if (!Double.isFinite(x) || !Double.isFinite(y)
                            || Math.abs(x) > Float.MAX_VALUE
                            || Math.abs(y) > Float.MAX_VALUE) {
                        LOGGER.warn(
                                "Ignoring invalid HUD position for module '{}', element '{}'",
                                moduleEntry.getKey(),
                                elementEntry.getKey());
                        continue;
                    }
                    modulePositions.put(
                            elementEntry.getKey(), new Position((float) x, (float) y));
                }
                result.put(moduleEntry.getKey(), modulePositions);
            }
        } catch (IOException
                | JsonParseException
                | IllegalStateException
                | NumberFormatException exception) {
            LOGGER.error("Could not read HUD layout file {}", CONFIG_FILE, exception);
        }
        return result;
    }

    private static void write() {
        JsonObject root = new JsonObject();
        POSITIONS.forEach(
                (moduleId, elements) -> {
                    JsonObject module = new JsonObject();
                    elements.forEach(
                            (elementId, position) -> {
                                JsonObject coordinates = new JsonObject();
                                coordinates.addProperty("x", position.x());
                                coordinates.addProperty("y", position.y());
                                module.add(elementId, coordinates);
                            });
                    root.add(moduleId, module);
                });
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException exception) {
            LOGGER.error("Could not save HUD layout file {}", CONFIG_FILE, exception);
        }
    }
}
