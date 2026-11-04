package dev.dlc.client.render.msdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;

public final class ResourceProvider {
    private static final ResourceManager RESOURCE_MANAGER = Minecraft.getInstance().getResourceManager();
    private static final Gson GSON = new Gson();

    public static Identifier getShaderIdentifier(String name) {
        return Identifier.fromNamespaceAndPath("mre", "core/" + name);
    }

    public static JsonObject toJson(Identifier identifier) {
        return JsonParser.parseString(toString(identifier)).getAsJsonObject();
    }

    public static <T> T fromJsonToInstance(Identifier identifier, Class<T> clazz) {
        return GSON.fromJson(toString(identifier), clazz);
    }

    public static String toString(Identifier identifier) {
        return toString(identifier, "\n");
    }

    public static String toString(Identifier identifier, String delimiter) {
        try (InputStream inputStream = RESOURCE_MANAGER.open(identifier);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining(delimiter));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
