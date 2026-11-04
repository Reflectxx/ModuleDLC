package dev.dlc.client.script;

import dev.dlc.client.hud.HudElement;
import dev.dlc.client.hud.HudImage;
import dev.dlc.client.hud.HudRectangle;
import dev.dlc.client.hud.HudText;
import dev.dlc.client.module.Module;
import dev.dlc.client.module.ModuleCategories;
import dev.dlc.client.setting.BooleanSetting;
import dev.dlc.client.setting.ColorSetting;
import dev.dlc.client.setting.ModeSetting;
import dev.dlc.client.setting.MultiBooleanSetting;
import dev.dlc.client.setting.NumberSetting;
import dev.dlc.client.setting.Setting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScriptApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleDLC/Scripts");

    private final String source;
    private final Consumer<Module> register;
    private final Consumer<ClientCommandManager.Command> registerCommand;

    ScriptApi(String source, Consumer<Module> register) {
        this(source, register, ignored -> {});
    }

    ScriptApi(
            String source,
            Consumer<Module> register,
            Consumer<ClientCommandManager.Command> registerCommand) {
        this.source = source;
        this.register = register;
        this.registerCommand = registerCommand;
    }

    public ScriptModuleBuilder module(String id, String name, String category) {
        return module(id, name, category, "");
    }

    public ScriptModuleBuilder module(String id, String name, String category, String description) {
        return new ScriptModuleBuilder(
                source, id, name, category, description, register, registerCommand);
    }

    public ScriptContext context() {
        return new ScriptContext(net.minecraft.client.Minecraft.getInstance(), null, source);
    }

    public ScriptStorage storage() {
        return ScriptStorage.forScript(source);
    }

    public void command(String name, ScriptCommandCallback callback) {
        registerCommand.accept(
                new ClientCommandManager.Command(
                        ClientCommandManager.normalizeName(name), source, Objects.requireNonNull(callback)));
    }

    public void log(String message) {
        LOGGER.info("[{}] {}", source, Objects.requireNonNullElse(message, ""));
    }

    public static final class ScriptModuleBuilder {
        private final String source;
        private final String id;
        private final String name;
        private final String category;
        private final String description;
        private final Consumer<Module> register;
        private final Consumer<ClientCommandManager.Command> registerCommand;
        private final List<Setting<?>> settings = new ArrayList<>();
        private final List<HudElement> hudElements = new ArrayList<>();
        private final Set<String> hudIds = new HashSet<>();
        private int nextHudId;
        private ScriptCallback onEnable;
        private ScriptCallback onDisable;
        private ScriptCallback onTick;
        private ScriptCallback onFrame;
        private ScriptWorldRenderCallback onWorldRender;
        private boolean registered;

        private ScriptModuleBuilder(
                String source,
                String id,
                String name,
                String category,
                String description,
                Consumer<Module> register,
                Consumer<ClientCommandManager.Command> registerCommand) {
            this.source = source;
            this.id = id;
            this.name = name;
            this.category = category;
            this.description = description;
            this.register = register;
            this.registerCommand = registerCommand;
        }

        public ScriptModuleBuilder setting(Setting<?> setting) {
            ensureNotRegistered();
            settings.add(Objects.requireNonNull(setting));
            return this;
        }

        public HudText hudText() {
            ensureNotRegistered();
            return addHudElement(
                    new HudText(nextHudId("text"), "", 0, 0, 8, 0xFFFFFFFFL, false));
        }

        public HudText hudText(String id) {
            ensureNotRegistered();
            return addHudElement(new HudText(id, "", 0, 0, 8, 0xFFFFFFFFL, false));
        }

        public HudText hudText(String id, String text, float x, float y) {
            return hudText(id, text, x, y, 8, 0xFFFFFFFFL, false);
        }

        public HudText hudText(
                String id,
                String text,
                float x,
                float y,
                float size,
                double color,
                boolean draggable) {
            ensureNotRegistered();
            return addHudElement(new HudText(id, text, x, y, size, color, draggable));
        }

        public HudRectangle hudRectangle() {
            ensureNotRegistered();
            return addHudElement(
                    new HudRectangle(
                            nextHudId("rectangle"), 0, 0, 80, 20, 0xB010171DL, false));
        }

        public HudRectangle hudRectangle(String id) {
            ensureNotRegistered();
            return addHudElement(
                    new HudRectangle(id, 0, 0, 80, 20, 0xB010171DL, false));
        }

        public HudRectangle hudRectangle(
                String id,
                float x,
                float y,
                float width,
                float height) {
            return hudRectangle(id, x, y, width, height, 0xB010171DL, true);
        }

        public HudRectangle hudRectangle(
                String id,
                float x,
                float y,
                float width,
                float height,
                double color,
                boolean draggable) {
            ensureNotRegistered();
            return addHudElement(
                    new HudRectangle(id, x, y, width, height, color, draggable));
        }

        public HudImage hudImage() {
            ensureNotRegistered();
            return addHudElement(
                    new HudImage(nextHudId("image"), null, 0, 0, 16, 16, 0xFFFFFFFFL, false));
        }

        public HudImage hudImage(String id) {
            ensureNotRegistered();
            return addHudElement(new HudImage(id, null, 0, 0, 16, 16, 0xFFFFFFFFL, false));
        }

        public HudImage hudImage(
                String id,
                String resource,
                float x,
                float y,
                float width,
                float height) {
            return hudImage(id, resource, x, y, width, height, 0xFFFFFFFFL, false);
        }

        public HudImage hudImage(
                String id,
                String resource,
                float x,
                float y,
                float width,
                float height,
                double tint,
                boolean draggable) {
            ensureNotRegistered();
            Objects.requireNonNull(resource);
            int separator = resource.indexOf(':');
            String namespace = separator < 0 ? "minecraft" : resource.substring(0, separator);
            String path = separator < 0 ? resource : resource.substring(separator + 1);
            if (namespace.isBlank() || path.isBlank() || path.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Invalid HUD image resource: " + resource);
            }
            Identifier texture = Identifier.fromNamespaceAndPath(namespace, path);
            return addHudElement(
                    new HudImage(id, texture, x, y, width, height, tint, draggable));
        }

        private String nextHudId(String type) {
            String id;
            do {
                id = "__" + type + "_" + ++nextHudId;
            } while (hudIds.contains(id));
            return id;
        }

        private <T extends HudElement> T addHudElement(T element) {
            ensureNotRegistered();
            if (!hudIds.add(element.id())) {
                throw new IllegalArgumentException("Duplicate HUD element id: " + element.id());
            }
            element.assignToModule(this);
            hudElements.add(element);
            return element;
        }

        public ScriptModuleBuilder toggle(String name, String hint, boolean initial) {
            toggleSetting(name, hint, initial);
            return this;
        }

        public BooleanSetting toggleSetting(String name, String hint, boolean initial) {
            BooleanSetting setting = new BooleanSetting(name, hint, initial);
            setting(setting);
            return setting;
        }

        public ScriptModuleBuilder number(
                String name,
                String hint,
                float min,
                float max,
                float step,
                float initial,
                String suffix) {
            numberSetting(name, hint, min, max, step, initial, suffix);
            return this;
        }

        public NumberSetting numberSetting(
                String name,
                String hint,
                float min,
                float max,
                float step,
                float initial,
                String suffix) {
            if (!Float.isFinite(min)
                    || !Float.isFinite(max)
                    || !Float.isFinite(step)
                    || !Float.isFinite(initial)
                    || min >= max
                    || step <= 0
                    || initial < min
                    || initial > max) {
                throw new IllegalArgumentException("Invalid numeric setting range or initial value");
            }
            NumberSetting setting =
                    new NumberSetting(name, hint, min, max, step, initial, suffix);
            setting(setting);
            return setting;
        }

        public ColorSetting colorSetting(String name, String hint, int argb) {
            ColorSetting setting = new ColorSetting(name, hint, argb);
            setting(setting);
            return setting;
        }

        public MultiBooleanSetting multiToggleSetting(
                String name, String hint, String commaSeparatedOptions) {
            List<BooleanSetting> options =
                    java.util.Arrays.stream(commaSeparatedOptions.split(","))
                            .map(String::strip)
                            .filter(option -> !option.isEmpty())
                            .map(option -> new BooleanSetting(option, "", false))
                            .toList();
            if (options.isEmpty()) {
                throw new IllegalArgumentException("At least one multi-toggle option is required");
            }
            MultiBooleanSetting setting = new MultiBooleanSetting(name, hint, options);
            setting(setting);
            return setting;
        }

        public ScriptModuleBuilder choice(
                String name, String hint, List<String> options, String initial) {
            choiceSetting(name, hint, options, initial);
            return this;
        }

        public ModeSetting choiceSetting(
                String name, String hint, List<String> options, String initial) {
            List<String> choices = List.copyOf(options);
            if (choices.isEmpty() || !choices.contains(initial)) {
                throw new IllegalArgumentException("Choice initial value must be one of its options");
            }
            ModeSetting setting = new ModeSetting(name, hint, choices, initial);
            setting(setting);
            return setting;
        }

        public ScriptModuleBuilder choice(
                String name, String hint, String commaSeparatedOptions, String initial) {
            List<String> options =
                    java.util.Arrays.stream(commaSeparatedOptions.split(","))
                            .map(String::strip)
                            .filter(option -> !option.isEmpty())
                            .toList();
            return choice(name, hint, options, initial);
        }

        public ModeSetting choiceSetting(
                String name, String hint, String commaSeparatedOptions, String initial) {
            List<String> options =
                    java.util.Arrays.stream(commaSeparatedOptions.split(","))
                            .map(String::strip)
                            .filter(option -> !option.isEmpty())
                            .toList();
            return choiceSetting(name, hint, options, initial);
        }

        public ModeSetting choiceSetting(
                String name, String commaSeparatedOptions, String initial) {
            List<String> options =
                    java.util.Arrays.stream(commaSeparatedOptions.split(","))
                            .map(String::strip)
                            .filter(option -> !option.isEmpty())
                            .toList();
            return choiceSetting(name, "", options, initial);
        }

        public ScriptModuleBuilder onEnable(ScriptCallback callback) {
            ensureNotRegistered();
            onEnable = Objects.requireNonNull(callback);
            return this;
        }

        public ScriptModuleBuilder onDisable(ScriptCallback callback) {
            ensureNotRegistered();
            onDisable = Objects.requireNonNull(callback);
            return this;
        }

        public ScriptModuleBuilder onTick(ScriptCallback callback) {
            ensureNotRegistered();
            onTick = Objects.requireNonNull(callback);
            return this;
        }

        public ScriptModuleBuilder onFrame(ScriptCallback callback) {
            ensureNotRegistered();
            onFrame = Objects.requireNonNull(callback);
            return this;
        }

        public ScriptModuleBuilder onWorldRender(ScriptWorldRenderCallback callback) {
            ensureNotRegistered();
            onWorldRender = Objects.requireNonNull(callback);
            return this;
        }

        public ScriptModuleBuilder command(String name, ScriptCommandCallback callback) {
            ensureNotRegistered();
            registerCommand.accept(
                    new ClientCommandManager.Command(
                            ClientCommandManager.normalizeName(name),
                            source,
                            Objects.requireNonNull(callback)));
            return this;
        }

        public void register() {
            ensureNotRegistered();
            registered = true;
            if (!ModuleCategories.contains(category)) {
                LOGGER.warn(
                        "Rejected module '{}' from {}: category '{}' does not exist",
                        id,
                        source,
                        category);
                ScriptErrorReporter.notifyUser("this category not exists");
                return;
            }
            register.accept(
                    new Module(
                            id,
                            name,
                            description,
                            category,
                            source,
                            settings,
                            hudElements,
                            onEnable,
                            onDisable,
                            onTick,
                            onFrame,
                            onWorldRender));
        }

        private void ensureNotRegistered() {
            if (registered) throw new IllegalStateException("Module is already registered");
        }
    }
}
