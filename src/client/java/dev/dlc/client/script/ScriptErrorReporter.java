package dev.dlc.client.script;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScriptErrorReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/Scripts");
    private static final Path LOG_FILE = FabricLoader.getInstance().getGameDir().resolve("mdlc.log");
    private static final ConcurrentLinkedQueue<String> NOTIFICATIONS =
            new ConcurrentLinkedQueue<>();

    private ScriptErrorReporter() {}

    public static void report(String source, String phase, Throwable error) {
        StringWriter stackTrace = new StringWriter();
        error.printStackTrace(new PrintWriter(stackTrace));
        String entry =
                "[" + Instant.now() + "] Script " + phase + " failed: " + source + "\n"
                        + stackTrace
                        + System.lineSeparator();
        try {
            synchronized (LOG_FILE) {
                Files.writeString(
                        LOG_FILE,
                        entry,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not write script error log {}", LOG_FILE, exception);
        }

        String message =
                phase.equals("load")
                        ? "Не удалось загрузить скрипт, см. лог: mdlc.log"
                        : "Ошибка выполнения скрипта, см. лог: mdlc.log";
        notifyUser(message);
    }

    public static void notifyUser(String message) {
        if (message != null && !message.isBlank()) NOTIFICATIONS.add(message);
    }

    public static void flushNotifications(Minecraft minecraft) {
        if (minecraft.player == null) return;
        String message;
        while ((message = NOTIFICATIONS.poll()) != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
