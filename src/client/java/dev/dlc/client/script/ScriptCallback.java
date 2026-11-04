package dev.dlc.client.script;

@FunctionalInterface
public interface ScriptCallback {
    void invoke(ScriptContext context) throws Exception;
}
