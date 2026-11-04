package dev.dlc.client.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientCommandManager {
    public record Command(String name, String source, ScriptCommandCallback callback) {}

    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/Commands");
    private static final ClientCommandManager INSTANCE = new ClientCommandManager();

    private final Map<String, List<Command>> commandsBySource = new HashMap<>();
    private final Map<String, Command> commands = new HashMap<>();

    private ClientCommandManager() {}

    public static ClientCommandManager get() {
        return INSTANCE;
    }

    public static String normalizeName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,48}"))
            throw new IllegalArgumentException("Command name must contain 1-48 letters, digits, '_' or '-'");
        return name.toLowerCase(Locale.ROOT);
    }

    public synchronized void validateSource(String source, List<Command> replacement) {
        Map<String, Command> candidate = new HashMap<>(commands);
        commandsBySource.getOrDefault(source, List.of()).forEach(command -> candidate.remove(command.name()));
        for (Command command : replacement) {
            if (candidate.putIfAbsent(command.name(), command) != null)
                throw new IllegalArgumentException("Duplicate client command: " + command.name());
        }
    }

    public synchronized void replaceSource(String source, List<Command> replacement) {
        validateSource(source, replacement);
        commandsBySource.getOrDefault(source, List.of()).forEach(command -> commands.remove(command.name()));
        List<Command> copy = List.copyOf(replacement);
        if (copy.isEmpty()) commandsBySource.remove(source);
        else commandsBySource.put(source, copy);
        copy.forEach(command -> commands.put(command.name(), command));
    }

    public synchronized void removeSource(String source) {
        List<Command> removed = commandsBySource.remove(source);
        if (removed != null) removed.forEach(command -> commands.remove(command.name()));
    }

    public boolean executeIfRecognized(String message) {
        if (message == null || !message.startsWith(".") || message.length() < 2) return false;
        int commandEnd = 1;
        while (commandEnd < message.length() && !Character.isWhitespace(message.charAt(commandEnd))) {
            commandEnd++;
        }
        String commandName = message.substring(1, commandEnd).toLowerCase(Locale.ROOT);
        Command command;
        synchronized (this) {
            command = commands.get(commandName);
        }
        if (command == null) return false;

        List<String> tokens;
        try {
            tokens = tokenize(message.substring(commandEnd));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid arguments for client command '{}': {}", command.name(), exception.getMessage());
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance()
                        .player
                        .displayClientMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "Invalid command arguments: " + exception.getMessage()),
                                false);
            }
            return true;
        }

        String[] args = tokens.toArray(String[]::new);
        try {
            command.callback().invoke(
                    new ScriptContext(Minecraft.getInstance(), null, command.source()), args);
        } catch (Exception exception) {
            LOGGER.error("Client command '{}' from {} failed", command.name(), command.source(), exception);
            ScriptErrorReporter.report(
                    command.source() + " (command " + command.name() + ")", "command", exception);
        }
        return true;
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        boolean tokenStarted = false;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (escaping) {
                token.append(current);
                escaping = false;
                tokenStarted = true;
            } else if (current == '\\' && quote != 0) {
                escaping = true;
            } else if (quote != 0) {
                if (current == quote) quote = 0;
                else token.append(current);
                tokenStarted = true;
            } else if (current == '"' || current == '\'') {
                quote = current;
                tokenStarted = true;
            } else if (Character.isWhitespace(current)) {
                if (tokenStarted) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else {
                token.append(current);
                tokenStarted = true;
            }
        }
        if (escaping || quote != 0) throw new IllegalArgumentException("Unclosed quoted argument");
        if (tokenStarted) tokens.add(token.toString());
        return List.copyOf(tokens);
    }
}
