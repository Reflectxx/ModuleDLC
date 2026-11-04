package dev.dlc.client.script;

import dev.dlc.client.module.Module;
import dev.dlc.client.module.ModuleManager;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScriptManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/Scripts");
    private static final Set<String> SCRIPT_EXTENSIONS = Set.of(".js", ".java", ".kt");

    private record CompiledScript(URLClassLoader loader, Path output) {}

    private final ModuleManager modules;
    private final Path directory =
            FabricLoader.getInstance().getConfigDir().resolve("moduledlc/scripts");
    private final Map<String, CompiledScript> compiledScripts = new HashMap<>();
    private WatchService watchService;
    private Thread watchThread;

    public ScriptManager(ModuleManager modules) {
        this.modules = modules;
    }

    public synchronized void start() {
        if (watchThread != null) return;
        try {
            Files.createDirectories(directory);
            watchService = FileSystems.getDefault().newWatchService();
            directory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize script directory " + directory, exception);
        }
        reloadAll();
        watchThread =
                new Thread(this::watch, "ModuleDLC-ScriptWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
        LOGGER.info("Watching scripts in {}", directory);
    }

    public synchronized void reloadAll() {
        Set<String> present = new HashSet<>();
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(ScriptManager::isSupported)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(
                            path -> {
                                String source = sourceId(path);
                                present.add(source);
                                reload(path, source);
                            });
        } catch (IOException exception) {
            LOGGER.error("Could not read script directory {}", directory, exception);
            return;
        }

        for (String source : knownSources()) {
            if (!present.contains(source)) removeSource(source);
        }
    }

    private synchronized Set<String> knownSources() {
        return new HashSet<>(compiledScripts.keySet());
    }

    private void reload(Path path, String source) {
        List<Module> staged = new ArrayList<>();
        CompiledScript nextScript = null;
        try {
            ScriptApi api = new ScriptApi(source, staged::add);
            String extension = extension(path);
            if (extension.equals(".js")) {
                loadJavaScript(path, api);
            } else if (extension.equals(".java")) {
                nextScript = loadJava(path, api);
            } else {
                nextScript = loadKotlin(path, api);
            }
            modules.replaceSource(source, staged);
            closeScript(compiledScripts.put(source, nextScript));
            LOGGER.info("Loaded {} module(s) from {}", staged.size(), path.getFileName());
        } catch (Exception exception) {
            closeScript(nextScript);
            LOGGER.error("Failed to load script {}; previous modules were kept", path, exception);
            ScriptErrorReporter.report(source, "load", exception);
        }
    }

    private void loadJavaScript(Path path, ScriptApi api) throws IOException {
        String source = Files.readString(path);
        Context context = ContextFactory.getGlobal().enterContext();
        try {
            context.setOptimizationLevel(-1);
            Scriptable scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", Context.javaToJS(api, scope, context));
            context.evaluateString(scope, source, path.getFileName().toString(), 1, null);
        } finally {
            context.close();
        }
    }

    private CompiledScript loadJava(Path path, ScriptApi api) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Java scripts require a JDK with the Java compiler");
        }
        Path output = Files.createTempDirectory("moduledlc-java-");
        URLClassLoader loader = null;
        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager =
                    compiler.getStandardFileManager(diagnostics, null, null)) {
                Iterable<? extends JavaFileObject> sourceFiles =
                        fileManager.getJavaFileObjectsFromPaths(List.of(path));
                List<String> options =
                        List.of(
                                "--release",
                                "21",
                                "-proc:none",
                                "-classpath",
                                scriptClasspath(),
                                "-d",
                                output.toString());
                if (!compiler.getTask(null, fileManager, diagnostics, options, null, sourceFiles).call()) {
                    throw new IllegalArgumentException("Java compilation failed:\n" + diagnostics.getDiagnostics());
                }
            }

            loader =
                    new URLClassLoader(
                            new URL[] {output.toUri().toURL()},
                            ScriptEntrypoint.class.getClassLoader());
            List<Class<?>> entrypoints = compiledClasses(output, loader);
            if (entrypoints.isEmpty()) throw new IllegalArgumentException("No ScriptEntrypoint implementation found");
            for (Class<?> entrypoint : entrypoints) {
                ((ScriptEntrypoint) entrypoint.getDeclaredConstructor().newInstance()).register(api);
            }
            return new CompiledScript(loader, output);
        } catch (Exception exception) {
            closeLoader(loader);
            deleteOutput(output);
            throw exception;
        }
    }

    private CompiledScript loadKotlin(Path path, ScriptApi api) throws Exception {
        Path output = Files.createTempFile("moduledlc-kotlin-", ".jar");
        Path log = Files.createTempFile("moduledlc-kotlinc-", ".log");
        Files.deleteIfExists(output);
        URLClassLoader loader = null;
        try {
            Process process =
                    new ProcessBuilder(
                                    "kotlinc",
                                    path.toAbsolutePath().toString(),
                                    "-classpath",
                                    scriptClasspath(),
                                    "-jvm-target",
                                    "21",
                                    "-include-runtime",
                                    "-d",
                                    output.toString())
                            .redirectOutput(log.toFile())
                            .redirectErrorStream(true)
                            .start();
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("Kotlin compilation timed out");
            }
            String compilerOutput = Files.readString(log);
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("Kotlin compilation failed:\n" + compilerOutput);
            }

            loader =
                    new URLClassLoader(
                            new URL[] {output.toUri().toURL()},
                            ScriptEntrypoint.class.getClassLoader());
            List<Class<?>> entrypoints = compiledClasses(output, loader);
            if (entrypoints.isEmpty()) throw new IllegalArgumentException("No ScriptEntrypoint implementation found");
            for (Class<?> entrypoint : entrypoints) {
                ((ScriptEntrypoint) entrypoint.getDeclaredConstructor().newInstance()).register(api);
            }
            return new CompiledScript(loader, output);
        } catch (Exception exception) {
            closeLoader(loader);
            deleteOutput(output);
            throw exception;
        } finally {
            Files.deleteIfExists(log);
        }
    }

    private static List<Class<?>> compiledClasses(Path output, URLClassLoader loader) throws IOException {
        List<Class<?>> entrypoints = new ArrayList<>();
        if (Files.isDirectory(output)) {
            try (var files = Files.walk(output)) {
                for (Path classFile : files.filter(file -> file.toString().endsWith(".class")).toList()) {
                    String name =
                            output.relativize(classFile)
                                    .toString()
                                    .replace('\\', '.')
                                    .replace('/', '.')
                                    .replaceAll("\\.class$", "");
                    loadEntrypoint(name, loader, entrypoints);
                }
            }
        } else {
            try (var jar = new java.util.jar.JarFile(output.toFile())) {
                var classes = jar.entries();
                while (classes.hasMoreElements()) {
                    String file = classes.nextElement().getName();
                    if (!file.endsWith(".class") || file.startsWith("kotlin/")) continue;
                    String name = file.replace('/', '.').replaceAll("\\.class$", "");
                    loadEntrypoint(name, loader, entrypoints);
                }
            }
        }
        return entrypoints;
    }

    private static void loadEntrypoint(
            String name, URLClassLoader loader, List<Class<?>> entrypoints) {
        if (name.contains("$") || name.equals("module-info")) return;
        try {
            Class<?> type = loader.loadClass(name);
            if (type != ScriptEntrypoint.class && ScriptEntrypoint.class.isAssignableFrom(type)) {
                entrypoints.add(type);
            }
        } catch (LinkageError | ClassNotFoundException exception) {
            throw new IllegalArgumentException("Could not load script class " + name, exception);
        }
    }

    private String scriptClasspath() {
        Set<String> entries = new HashSet<>();
        String current = System.getProperty("java.class.path", "");
        if (!current.isBlank()) entries.add(current);
        addCodeLocation(entries, ScriptEntrypoint.class);
        addCodeLocation(entries, ScriptApi.class);
        addCodeLocation(entries, Module.class);
        addCodeLocation(entries, net.minecraft.client.Minecraft.class);
        addCodeLocation(entries, net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.class);
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static void addCodeLocation(Set<String> entries, Class<?> type) {
        try {
            entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not locate script API class " + type.getName(), exception);
        }
    }

    private void watch() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                Set<Path> changed = new HashSet<>();
                collectEvents(key, changed);
                Thread.sleep(300);
                WatchKey next;
                while ((next = watchService.poll()) != null) collectEvents(next, changed);
                if (!changed.isEmpty()) {
                    Minecraft.getInstance()
                            .execute(() -> changed.forEach(this::reloadOrRemove));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ClosedWatchServiceException exception) {
                break;
            } catch (RuntimeException exception) {
                LOGGER.error("Script watcher failed", exception);
            }
        }
    }

    private void collectEvents(WatchKey key, Set<Path> changed) {
        for (var event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                Minecraft.getInstance().execute(this::reloadAll);
            } else if (event.context() instanceof Path path && isSupported(path)) {
                changed.add(directory.resolve(path));
            }
        }
        if (!key.reset()) LOGGER.warn("Script directory watcher is no longer valid");
    }

    private void reloadOrRemove(Path path) {
        String source = sourceId(path);
        if (Files.isRegularFile(path) && isSupported(path)) {
            reload(path, source);
        } else if (knownSources().contains(source)) {
            removeSource(source);
        }
    }

    private void removeSource(String source) {
        modules.removeSource(source);
        closeScript(compiledScripts.remove(source));
        LOGGER.info("Unloaded script {}", source);
    }

    private static void closeScript(CompiledScript script) {
        if (script == null) return;
        closeLoader(script.loader());
        deleteOutput(script.output());
    }

    private static void deleteOutput(Path output) {
        try {
            if (Files.isDirectory(output)) {
                try (var files = Files.walk(output)) {
                    for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            } else {
                Files.deleteIfExists(output);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not remove temporary script output {}", output, exception);
        }
    }

    private static void closeLoader(URLClassLoader loader) {
        if (loader == null) return;
        try {
            loader.close();
        } catch (IOException exception) {
            LOGGER.warn("Could not close script class loader", exception);
        }
    }

    private static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return SCRIPT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT);
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
                LOGGER.warn("Could not close script watcher", exception);
            }
        }
        if (watchThread != null) watchThread.interrupt();
        for (String source : knownSources()) removeSource(source);
        watchThread = null;
        watchService = null;
    }
}
