package dev.dlc.client.script;

import java.util.List;

@FunctionalInterface
public interface ScriptCommandCallback {
    void invoke(ScriptContext context, String[] args) throws Exception;
}
