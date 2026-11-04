package dev.dlc.client;

import dev.dlc.ModuleDLC;
import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.exceptions.PipeAccessDenied;
import dev.firstdark.rpc.exceptions.UnsupportedOsType;
import dev.firstdark.rpc.handlers.RPCEventHandler;
import dev.firstdark.rpc.models.DiscordRichPresence;
import dev.firstdark.rpc.models.User;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class DiscordRpcManager {
    private static final String APPLICATION_ID = "1567466330922295306";
    private static final String SITE_URL = "https://reflectxx.github.io";
    private static final String DOCS_URL = "https://reflectxx.github.io/docs";
    private static final Identifier AVATAR_TEXTURE =
            Identifier.fromNamespaceAndPath("moduledlc", "dynamic/discord_avatar");
    private static volatile String username;
    private static volatile Identifier avatarTexture;

    private final DiscordRpc rpc = new DiscordRpc();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicLong avatarRequest = new AtomicLong();

    public void start() {
        try {
            rpc.init(APPLICATION_ID, new RPCEventHandler() {
                @Override
                public void ready(User user) {
                    username =
                            user.getGlobalName() != null && !user.getGlobalName().isBlank()
                                    ? user.getGlobalName()
                                    : user.getUsername();
                    ModuleDLC.LOGGER.info(
                            "Discord RPC connected; custom avatar available: {}",
                            user.getAvatar() != null && !user.getAvatar().isBlank());
                    loadAvatar(user);
                }

                @Override
                public void disconnected(
                        dev.firstdark.rpc.enums.ErrorCode errorCode, String message) {
                    ModuleDLC.LOGGER.warn("Discord RPC disconnected: {} ({})", message, errorCode);
                    clearProfile();
                }

                @Override
                public void errored(dev.firstdark.rpc.enums.ErrorCode errorCode, String message) {
                    ModuleDLC.LOGGER.warn("Discord RPC error {}: {}", errorCode, message);
                }
            }, false);
            rpc.updatePresence(DiscordRichPresence.builder()
                    .details("The Open Source Platform for Script testing")
                    .state("In Minecraft")
                    .button(DiscordRichPresence.RPCButton.of("Site", SITE_URL))
                    .button(DiscordRichPresence.RPCButton.of("Docs", DOCS_URL))
                    .build());
        } catch (UnsupportedOsType | PipeAccessDenied e) {
            ModuleDLC.LOGGER.warn("Unable to initialize Discord Rich Presence", e);
            clearProfile();
        }
    }

    public void stop() {
        rpc.shutdown();
        clearProfile();
    }

    public static String username() {
        return username;
    }

    public static Identifier avatarTexture() {
        return avatarTexture;
    }

    private void loadAvatar(User user) {
        long requestId = avatarRequest.incrementAndGet();
        avatarTexture = null;
        Minecraft.getInstance()
                .execute(
                        () -> {
                            if (requestId == avatarRequest.get()) {
                                Minecraft.getInstance().getTextureManager().release(AVATAR_TEXTURE);
                            }
                        });
        String userId = user.getUserId();
        if (userId == null || !userId.matches("\\d+")) {
            return;
        }

        String avatar = user.getAvatar();
        String url;
        if (avatar != null && avatar.matches("[a-zA-Z0-9_]{1,128}")) {
            url =
                    "https://cdn.discordapp.com/avatars/"
                            + userId
                            + "/"
                            + avatar
                            + ".png?size=64&format=png";
        } else {
            try {
                long snowflake = Long.parseUnsignedLong(userId);
                url =
                        "https://cdn.discordapp.com/embed/avatars/"
                                + ((snowflake >>> 22) % 6)
                                + ".png?size=64&format=png";
            } catch (NumberFormatException e) {
                return;
            }
        }

        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "ModuleDLC")
                        .header("Accept", "image/png")
                        .GET()
                        .build();
        httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .whenComplete(
                        (response, error) -> {
                            if (error != null) {
                                if (requestId != avatarRequest.get()) return;
                                ModuleDLC.LOGGER.warn("Unable to download Discord avatar", error);
                                return;
                            }
                            try (InputStream body = response.body()) {
                                if (requestId != avatarRequest.get()) return;
                                if (response.statusCode() != 200) {
                                    ModuleDLC.LOGGER.warn(
                                            "Unable to load Discord avatar (HTTP {})",
                                            response.statusCode());
                                    return;
                                }
                                byte[] bytes = body.readNBytes(512 * 1024 + 1);
                                if (bytes.length > 512 * 1024) {
                                    ModuleDLC.LOGGER.warn("Discord avatar exceeds 512 KiB");
                                    return;
                                }

                                NativeImage image;
                                try {
                                    image = NativeImage.read(new ByteArrayInputStream(bytes));
                                } catch (IOException e) {
                                    ModuleDLC.LOGGER.warn("Unable to decode Discord avatar", e);
                                    return;
                                }
                                if (image.getWidth() > 256
                                        || image.getHeight() > 256
                                        || image.getWidth() == 0
                                        || image.getHeight() == 0) {
                                    image.close();
                                    ModuleDLC.LOGGER.warn("Discord avatar has invalid dimensions");
                                    return;
                                }

                                Minecraft minecraft = Minecraft.getInstance();
                                minecraft.execute(
                                        () -> {
                                            if (requestId != avatarRequest.get()) {
                                                image.close();
                                                return;
                                            }
                                            minecraft.getTextureManager().release(AVATAR_TEXTURE);
                                            minecraft
                                                    .getTextureManager()
                                                    .register(
                                                            AVATAR_TEXTURE,
                                                            new DynamicTexture(
                                                                    () -> "ModuleDLC Discord avatar",
                                                                    image));
                                            avatarTexture = AVATAR_TEXTURE;
                                            ModuleDLC.LOGGER.info("Discord avatar loaded");
                                        });
                            } catch (IOException e) {
                                if (requestId == avatarRequest.get()) {
                                    ModuleDLC.LOGGER.warn("Unable to read Discord avatar response", e);
                                }
                            }
                        });
    }

    private void clearProfile() {
        username = null;
        avatarTexture = null;
        long requestId = avatarRequest.incrementAndGet();
        Minecraft.getInstance()
                .execute(
                        () -> {
                            if (requestId == avatarRequest.get()) {
                                Minecraft.getInstance().getTextureManager().release(AVATAR_TEXTURE);
                            }
                        });
    }
}
