package dev.dlc.client.ui.panel;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ThemeManager implements AutoCloseable {
    public record Theme(String id, String name, Map<String, Integer> colors) {}

    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/Themes");
    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final Theme DEFAULT_THEME = new Theme("default", "Default", Map.of());

    private final Path directory =
            FabricLoader.getInstance().getConfigDir().resolve("moduledlc/themes");
    private final Path activeThemeFile =
            FabricLoader.getInstance().getConfigDir().resolve("moduledlc/active-theme.txt");
    private final Map<String, List<Theme>> themesBySource = new HashMap<>();
    private WatchService watchService;
    private Thread watchThread;
    private String activeThemeId = "default";

    private ThemeManager() {}

    public static ThemeManager get() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (watchThread != null) return;
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(activeThemeFile))
                activeThemeId = Files.readString(activeThemeFile).strip();
            watchService = FileSystems.getDefault().newWatchService();
            directory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize theme directory " + directory, exception);
        }
        reloadAll();
        watchThread = new Thread(this::watch, "ModuleDLC-ThemeWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
        LOGGER.info("Watching themes in {}", directory);
    }

    public synchronized List<Theme> themes() {
        List<Theme> themes = new ArrayList<>();
        themes.add(DEFAULT_THEME);
        themesBySource.values().forEach(themes::addAll);
        return themes.stream().sorted(Comparator.comparing(Theme::name)).toList();
    }

    public synchronized Theme activeTheme() {
        return themes().stream()
                .filter(theme -> theme.id().equals(activeThemeId))
                .findFirst()
                .orElse(DEFAULT_THEME);
    }

    public synchronized boolean isActive(Theme theme) {
        return activeTheme().id().equals(theme.id());
    }

    public synchronized void setActiveTheme(Theme theme) {
        if (themes().stream().noneMatch(available -> available.id().equals(theme.id()))) return;
        activeThemeId = theme.id();
        PanelStyle.applyTheme(theme);
        try {
            Files.createDirectories(activeThemeFile.getParent());
            Files.writeString(activeThemeFile, activeThemeId);
        } catch (IOException exception) {
            LOGGER.error("Could not save selected theme {}", activeThemeId, exception);
        }
    }

    private synchronized void reloadAll() {
        Set<String> present = new HashSet<>();
        try (var files = Files.list(directory)) {
            for (Path path :
                    files.filter(Files::isRegularFile)
                            .filter(ThemeManager::isThemeScript)
                            .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                            .toList()) {
                String source = sourceId(path);
                present.add(source);
                reload(path, source);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not read theme directory {}", directory, exception);
            return;
        }
        themesBySource.keySet().removeIf(source -> !present.contains(source));
        applyActiveTheme();
    }

    private synchronized void reload(Path path, String source) {
        List<Theme> staged = new ArrayList<>();
        try {
            Context context = ContextFactory.getGlobal().enterContext();
            try {
                context.setOptimizationLevel(-1);
                Scriptable scope = context.initStandardObjects();
                ThemeApi api = new ThemeApi(staged::add);
                ScriptableObject.putProperty(scope, "api", Context.javaToJS(api, scope, context));
                context.evaluateString(
                        scope, Files.readString(path), path.getFileName().toString(), 1, null);
            } finally {
                context.close();
            }
            if (staged.isEmpty()) throw new IllegalArgumentException("Theme script registered no themes");
            validateUniqueIds(source, staged);
            themesBySource.put(source, List.copyOf(staged));
            LOGGER.info("Loaded {} theme(s) from {}", staged.size(), path.getFileName());
            applyActiveTheme();
        } catch (Exception exception) {
            LOGGER.error("Failed to load theme script {}; previous themes were kept", path, exception);
        }
    }

    private void validateUniqueIds(String source, List<Theme> staged) {
        Set<String> ids = new HashSet<>();
        for (Theme theme : staged) {
            if (!ids.add(theme.id())) throw new IllegalArgumentException("Duplicate theme id: " + theme.id());
            for (Map.Entry<String, List<Theme>> entry : themesBySource.entrySet()) {
                if (entry.getKey().equals(source)) continue;
                if (entry.getValue().stream().anyMatch(existing -> existing.id().equals(theme.id())))
                    throw new IllegalArgumentException("Theme id is already registered: " + theme.id());
            }
        }
    }

    private synchronized void applyActiveTheme() {
        Theme active = themes().stream()
                .filter(theme -> theme.id().equals(activeThemeId))
                .findFirst()
                .orElse(DEFAULT_THEME);
        activeThemeId = active.id();
        PanelStyle.applyTheme(active);
    }

    private void watch() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var key = watchService.take();
                Set<Path> changed = new HashSet<>();
                collectEvents(key, changed);
                Thread.sleep(250);
                var next = watchService.poll();
                while (next != null) {
                    collectEvents(next, changed);
                    next = watchService.poll();
                }
                if (!changed.isEmpty())
                    Minecraft.getInstance().execute(() -> changed.forEach(this::reloadOrRemove));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ClosedWatchServiceException exception) {
                break;
            } catch (RuntimeException exception) {
                LOGGER.error("Theme watcher failed", exception);
            }
        }
    }

    private void collectEvents(java.nio.file.WatchKey key, Set<Path> changed) {
        for (var event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                Minecraft.getInstance().execute(this::reloadAll);
            } else if (event.context() instanceof Path path && isThemeScript(path)) {
                changed.add(directory.resolve(path));
            }
        }
        if (!key.reset()) LOGGER.warn("Theme directory watcher is no longer valid");
    }

    private synchronized void reloadOrRemove(Path path) {
        String source = sourceId(path);
        if (Files.isRegularFile(path) && isThemeScript(path)) reload(path, source);
        else {
            themesBySource.remove(source);
            applyActiveTheme();
        }
    }

    private static boolean isThemeScript(Path path) {
        return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".js");
    }

    private static String sourceId(Path path) {
        return path.getFileName().toString();
    }

    @Override
    public synchronized void close() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException exception) {
                LOGGER.warn("Could not close theme watcher", exception);
            }
        }
        if (watchThread != null) watchThread.interrupt();
        watchService = null;
        watchThread = null;
    }
}
