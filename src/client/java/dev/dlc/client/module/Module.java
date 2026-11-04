package dev.dlc.client.module;

import dev.dlc.client.hud.HudElement;
import dev.dlc.client.render.ui.UiDrawList;
import dev.dlc.client.script.ScriptCallback;
import dev.dlc.client.script.ScriptContext;
import dev.dlc.client.script.ScriptErrorReporter;
import dev.dlc.client.setting.Setting;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Module {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/Scripts");

    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final String source;
    private final List<Setting<?>> settings;
    private final List<HudElement> hudElements;
    private final ScriptCallback onEnable;
    private final ScriptCallback onDisable;
    private final ScriptCallback onTick;
    private final ScriptCallback onFrame;
    private boolean enabled;
    private boolean error;

    public Module(
            String id,
            String name,
            String description,
            String category,
            String source,
            List<Setting<?>> settings,
            ScriptCallback onEnable,
            ScriptCallback onDisable,
            ScriptCallback onTick,
            ScriptCallback onFrame) {
        this(
                id,
                name,
                description,
                category,
                source,
                settings,
                List.of(),
                onEnable,
                onDisable,
                onTick,
                onFrame);
    }

    public Module(
            String id,
            String name,
            String description,
            String category,
            String source,
            List<Setting<?>> settings,
            List<HudElement> hudElements,
            ScriptCallback onEnable,
            ScriptCallback onDisable,
            ScriptCallback onTick,
            ScriptCallback onFrame) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.description = Objects.requireNonNullElse(description, "");
        this.category = requireText(category, "category");
        this.source = requireText(source, "source");
        this.settings = List.copyOf(settings);
        this.hudElements = List.copyOf(hudElements);
        this.onEnable = onEnable;
        this.onDisable = onDisable;
        this.onTick = onTick;
        this.onFrame = onFrame;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String category() {
        return category;
    }

    public String source() {
        return source;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean hasError() {
        return error;
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    public List<HudElement> hudElements() {
        return hudElements;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        ScriptContext context =
                new ScriptContext(net.minecraft.client.Minecraft.getInstance(), this);
        if (!invoke(value ? onEnable : onDisable, context) && value) {
            enabled = false;
            invoke(onDisable, context);
        }
    }

    public void disable() {
        setEnabled(false);
    }

    public void tick(ScriptContext context) {
        if (!invoke(onTick, context)) {
            enabled = false;
            invoke(onDisable, new ScriptContext(context.minecraft(), this));
        }
    }

    public void frame(ScriptContext context) {
        if (!enabled) return;
        if (!invoke(onFrame, context)) {
            enabled = false;
            invoke(onDisable, new ScriptContext(context.minecraft(), this));
        }
    }

    public void renderHud(UiDrawList ui) {
        if (!enabled) return;
        try {
            for (HudElement element : hudElements) {
                if (!element.attached()) element.draw(ui);
            }
        } catch (Exception exception) {
            error = true;
            LOGGER.error("HUD rendering failed in module '{}'; disabling it", id, exception);
            ScriptErrorReporter.report(source + " (module " + id + ")", "HUD render", exception);
            setEnabled(false);
        }
    }

    private boolean invoke(ScriptCallback callback, ScriptContext context) {
        if (callback == null) return true;
        try {
            callback.invoke(context);
            return true;
        } catch (Exception exception) {
            error = true;
            LOGGER.error("Script callback failed in module '{}'; disabling it", id, exception);
            ScriptErrorReporter.report(source + " (module " + id + ")", "callback", exception);
            return false;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
