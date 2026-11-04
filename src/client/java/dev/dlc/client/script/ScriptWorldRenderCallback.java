package dev.dlc.client.script;

@FunctionalInterface
public interface ScriptWorldRenderCallback {
    void invoke(ScriptContext context, ScriptRenderContext renderContext) throws Exception;
}
