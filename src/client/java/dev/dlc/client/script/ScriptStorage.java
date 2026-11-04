package dev.dlc.client.script;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;

public final class ScriptStorage {
    private static final Gson GSON = new Gson();
    private static final Path DIRECTORY =
            FabricLoader.getInstance().getConfigDir().resolve("moduledlc/storage");
    private static final Map<String, ScriptStorage> INSTANCES = new ConcurrentHashMap<>();

    private final String namespace;
    private final Path file;
    private final Map<String, String> values;

    private ScriptStorage(String namespace) {
        this.namespace = namespace;
        file = DIRECTORY.resolve(hash(namespace) + ".json");
        values = load();
    }

    static ScriptStorage forScript(String namespace) {
        return INSTANCES.computeIfAbsent(namespace, ScriptStorage::new);
    }

    public synchronized String get(String key) {
        return values.get(requireKey(key));
    }

    public synchronized void set(String key, String value) {
        String checkedKey = requireKey(key);
        if (value == null) throw new IllegalArgumentException("Storage value must not be null");
        if (value.length() > 65_536)
            throw new IllegalArgumentException("Storage value exceeds 65536 characters");
        values.put(checkedKey, value);
        save();
    }

    public synchronized boolean remove(String key) {
        boolean removed = values.remove(requireKey(key)) != null;
        if (removed) save();
        return removed;
    }

    public synchronized List<String> keys() {
        return List.copyOf(values.keySet());
    }

    private Map<String, String> load() {
        Map<String, String> loaded = new TreeMap<>();
        if (!Files.isRegularFile(file)) return loaded;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isString()) {
                    loaded.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Could not read persistent storage for script " + namespace, exception);
        }
        return loaded;
    }

    private void save() {
        try {
            Files.createDirectories(DIRECTORY);
            JsonObject json = new JsonObject();
            values.forEach(json::addProperty);
            Path temporary = Files.createTempFile(DIRECTORY, file.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
                try {
                    Files.move(
                            temporary,
                            file,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not save persistent storage for script " + namespace, exception);
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128)
            throw new IllegalArgumentException("Storage key must contain 1-128 characters");
        return key;
    }

    private static String hash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) result.append("%02x".formatted(part & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
