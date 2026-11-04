package dev.dlc.client.module;

import dev.dlc.client.hud.HudLayoutStore;
import dev.dlc.client.script.ScriptContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class ModuleManager {
    private final Map<String, List<Module>> bySource = new LinkedHashMap<>();
    private long revision;

    public synchronized List<Module> all() {
        return bySource.values().stream().flatMap(List::stream).toList();
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized void replaceSource(String source, List<Module> modules) {
        List<Module> replacement = List.copyOf(modules);
        Map<String, Module> ids = new LinkedHashMap<>();
        Map<String, Boolean> enabledById = new LinkedHashMap<>();
        for (Module module : all()) {
            if (!source.equals(module.source())) {
                ids.put(module.id(), module);
            } else {
                enabledById.put(module.id(), module.enabled());
            }
        }
        for (Module module : replacement) {
            if (ids.putIfAbsent(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate module id: " + module.id());
            }
        }

        List<Module> previous = bySource.get(source);
        if (previous != null) HudLayoutStore.saveModules(previous);
        for (Module module : replacement) HudLayoutStore.restore(module);
        previous = bySource.remove(source);
        if (previous != null) previous.forEach(Module::disable);
        if (!replacement.isEmpty()) {
            bySource.put(source, replacement);
            for (Module module : replacement) {
                if (enabledById.getOrDefault(module.id(), false)) module.setEnabled(true);
            }
        }
        revision++;
    }

    public synchronized void removeSource(String source) {
        List<Module> removed = bySource.get(source);
        if (removed == null) return;
        HudLayoutStore.saveModules(removed);
        bySource.remove(source);
        removed.forEach(Module::disable);
        revision++;
    }

    public void tick(Minecraft minecraft) {
        List<Module> snapshot = all();
        for (Module module : snapshot) {
            if (module.enabled()) module.tick(new ScriptContext(minecraft, module));
        }
    }

    public void frame(Minecraft minecraft) {
        List<Module> snapshot = all();
        for (Module module : snapshot) {
            if (module.enabled()) module.frame(new ScriptContext(minecraft, module));
        }
    }
}
