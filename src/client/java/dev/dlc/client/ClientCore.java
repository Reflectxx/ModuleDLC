package dev.dlc.client;

import dev.dlc.client.module.ModuleManager;
import dev.dlc.client.render.Renderer;
import dev.dlc.client.script.ScriptManager;

public class ClientCore {
    public static final String NAME = "ModuleDLC";
    private static ClientCore instance;
    private final ModuleManager modules = new ModuleManager();
    private final ScriptManager scripts = new ScriptManager(modules);
    private final Renderer renderer = new Renderer();

    public static ClientCore getInstance() {
        if (instance == null) instance = new ClientCore();
        return instance;
    }

    public Renderer renderer() { return renderer; }
    public ModuleManager modules() { return modules; }
    public ScriptManager scripts() { return scripts; }
}
