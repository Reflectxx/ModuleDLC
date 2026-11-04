package dev.dlc.client.ui.panel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class ThemeApi {
    private final Consumer<ThemeManager.Theme> register;

    ThemeApi(Consumer<ThemeManager.Theme> register) {
        this.register = register;
    }

    public ThemeBuilder theme(String id, String name) {
        return new ThemeBuilder(id, name, register);
    }

    public static final class ThemeBuilder {
        private final String id;
        private final String name;
        private final Consumer<ThemeManager.Theme> register;
        private final Map<String, Integer> colors = new LinkedHashMap<>();
        private boolean registered;

        private ThemeBuilder(String id, String name, Consumer<ThemeManager.Theme> register) {
            this.id = Objects.requireNonNull(id).strip();
            this.name = Objects.requireNonNull(name).strip();
            this.register = register;
            if (!this.id.matches("[a-zA-Z0-9._-]{1,48}"))
                throw new IllegalArgumentException("Invalid theme id: " + id);
            if (this.id.equalsIgnoreCase("default"))
                throw new IllegalArgumentException("Theme id 'default' is reserved");
            if (this.name.isBlank() || this.name.length() > 48)
                throw new IllegalArgumentException("Theme name must contain 1-48 characters");
        }

        public ThemeBuilder color(String role, String value) {
            if (registered) throw new IllegalStateException("Theme is already registered");
            if (!PanelStyle.colorRoles().contains(role))
                throw new IllegalArgumentException("Unknown theme color role: " + role);
            colors.put(role, parseColor(value));
            return this;
        }

        public void register() {
            if (registered) throw new IllegalStateException("Theme is already registered");
            if (colors.isEmpty()) throw new IllegalStateException("Theme must define at least one color");
            registered = true;
            register.accept(new ThemeManager.Theme(id, name, Map.copyOf(colors)));
        }

        private static int parseColor(String value) {
            String hex = Objects.requireNonNull(value).strip();
            if (!hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
                throw new IllegalArgumentException(
                        "Color must be #RRGGBB or #RRGGBBAA: " + value);
            long parsed = Long.parseLong(hex.substring(1), 16);
            if (hex.length() == 7) return (int) (0xFF000000L | parsed);
            return (int) ((parsed << 24) | (parsed >>> 8));
        }
    }
}
