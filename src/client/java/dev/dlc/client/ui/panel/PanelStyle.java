package dev.dlc.client.ui.panel;

import dev.dlc.client.render.ui.UiDrawList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Geometry, palette and icon mapping for the sidebar panel. */
final class PanelStyle {
    static int ACCENT = 0xFFA8C0D0;
    static int TEXT = 0xFFE8EEF1;
    static int SETTING_TEXT = 0xFFD4E0E6;
    static int MUTED = 0xFF84929B;
    static int SECTION_TEXT = 0xFF65727A;
    static int DISABLED_TEXT = 0xFFA6B4BC;
    static int PANEL = 0xFF101418;
    static int CARD = 0xFF141A20;
    static int SURFACE = 0xFF1B2228;
    static int BORDER = 0xFF242D35;
    static int ERROR = 0xFFE45B62;
    static int ENABLED = 0xFFCBD8DE;
    static int DISABLED = 0xFF2B343B;
    static int HOVER = 0xFF1B242A;
    static int SELECTED = 0xFFAFBBC2;
    static int FAVORITE = 0xFFF3CE80;
    static int SLIDER_TRACK = 0xFF2A3035;
    static int POPUP_BACKGROUND = 0xFF171717;
    static int POPUP_SURFACE = 0xFF1B2228;
    static int POPUP_BORDER = 0xFF39454B;
    static int WHITE = 0xFFF5F5F5;
    static int SHADOW = 0x49000000;

    private static final Map<String, Integer> DEFAULT_COLORS =
            Map.ofEntries(
                    Map.entry("background", 0xFF101418),
                    Map.entry("surface", 0xFF141A20),
                    Map.entry("elevatedSurface", 0xFF1B2228),
                    Map.entry("border", 0xFF242D35),
                    Map.entry("accent", 0xFFA8C0D0),
                    Map.entry("text", 0xFFE8EEF1),
                    Map.entry("settingText", 0xFFD4E0E6),
                    Map.entry("mutedText", 0xFF84929B),
                    Map.entry("sectionText", 0xFF65727A),
                    Map.entry("disabledText", 0xFFA6B4BC),
                    Map.entry("error", 0xFFE45B62),
                    Map.entry("enabled", 0xFFCBD8DE),
                    Map.entry("disabled", 0xFF2B343B),
                    Map.entry("hover", 0xFF1B242A),
                    Map.entry("selected", 0xFFAFBBC2),
                    Map.entry("favorite", 0xFFF3CE80),
                    Map.entry("sliderTrack", 0xFF2A3035),
                    Map.entry("popupBackground", 0xFF171717),
                    Map.entry("popupSurface", 0xFF1B2228),
                    Map.entry("popupBorder", 0xFF39454B),
                    Map.entry("white", 0xFFF5F5F5),
                    Map.entry("shadow", 0x49000000));
    private static final List<String> COLOR_ROLE_ORDER =
            List.of(
                    "background", "surface", "elevatedSurface", "border", "accent", "text",
                    "settingText", "mutedText", "sectionText", "disabledText", "error", "enabled",
                    "disabled", "hover", "selected", "favorite", "sliderTrack", "popupBackground",
                    "popupSurface", "popupBorder", "white", "shadow");

    private PanelStyle() {}

    static Set<String> colorRoles() {
        return DEFAULT_COLORS.keySet();
    }

    static Map<String, Integer> defaultColors() {
        return DEFAULT_COLORS;
    }

    static List<String> colorRoleOrder() {
        return COLOR_ROLE_ORDER;
    }

    static void applyTheme(ThemeManager.Theme theme) {
        Map<String, Integer> colors = new LinkedHashMap<>(DEFAULT_COLORS);
        colors.putAll(theme.colors());
        ACCENT = colors.get("accent");
        TEXT = colors.get("text");
        SETTING_TEXT = colors.get("settingText");
        MUTED = colors.get("mutedText");
        SECTION_TEXT = colors.get("sectionText");
        DISABLED_TEXT = colors.get("disabledText");
        PANEL = colors.get("background");
        CARD = colors.get("surface");
        SURFACE = colors.get("elevatedSurface");
        BORDER = colors.get("border");
        ERROR = colors.get("error");
        ENABLED = colors.get("enabled");
        DISABLED = colors.get("disabled");
        HOVER = colors.get("hover");
        SELECTED = colors.get("selected");
        FAVORITE = colors.get("favorite");
        SLIDER_TRACK = colors.get("sliderTrack");
        POPUP_BACKGROUND = colors.get("popupBackground");
        POPUP_SURFACE = colors.get("popupSurface");
        POPUP_BORDER = colors.get("popupBorder");
        WHITE = colors.get("white");
        SHADOW = colors.get("shadow");
    }

    /** Closed-form critically damped motion. Retargeting preserves velocity and position. */
    static final class Motion {
        private float position;
        private float velocity;
        private float target;

        Motion(float initial) {
            position = target = unit(initial);
        }

        void setTarget(float value) {
            target = unit(value);
        }

        float update(float deltaSeconds, float response) {
            float elapsed = Math.max(0, Math.min(.1f, deltaSeconds));
            float frequency = Math.max(0, response);
            float error = position - target;
            float impulse = velocity + frequency * error;
            float decay = (float) Math.exp(-frequency * elapsed);
            position = target + (error + impulse * elapsed) * decay;
            velocity = (velocity - frequency * impulse * elapsed) * decay;
            return value();
        }

        float value() {
            return unit(position);
        }

        private static float unit(float value) {
            return Math.max(0, Math.min(1, value));
        }
    }

    record Layout(float x, float y, float w, float h, float s) {
        static Layout create(int width, int height, float entrance) {
            float s = Math.max(.05f, Math.min(width * .82f / 620, height * .84f / 430)) * .8f;
            return new Layout(
                    (width - 620 * s) / 2,
                    (height - 430 * s) / 2 + (1 - entrance) * 10 * s,
                    620 * s,
                    430 * s,
                    s);
        }

        float sidebar() {
            return 158 * s;
        }

        float contentX() {
            return x + sidebar() + 12 * s;
        }

        float contentY() {
            return y + 40 * s;
        }

        float contentW() {
            return w - sidebar() - 24 * s;
        }

        float contentH() {
            return h - 50 * s;
        }

        float cardW() {
            return (contentW() - 6 * s) / 2;
        }

        boolean containsContent(float mx, float my) {
            return contains(mx, my, contentX(), contentY(), contentW(), contentH());
        }
    }

    static boolean contains(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    static int alpha(int color, float opacity) {
        return color & 0xFFFFFF
                | Math.round((color >>> 24) * Math.max(0, Math.min(1, opacity))) << 24;
    }

    static int blend(int from, int to, float p) {
        float t = Math.max(0, Math.min(1, p));
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            color |=
                    Math.round(((from >>> shift) & 255) * (1 - t) + ((to >>> shift) & 255) * t)
                            << shift;
        }
        return color;
    }

    static String fit(String text, float width, float size) {
        int count = Math.max(1, (int) (width / (size * .58f)));
        return text.length() <= count ? text : text.substring(0, Math.max(0, count - 1)) + "…";
    }

    static List<String> wrap(String text, float width, float size, int maxLines) {
        if (text == null || text.isBlank()) return List.of();
        int limit = Math.max(6, (int) (width / (size * .53f)));
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > limit) {
                lines.add(line.toString());
                line.setLength(0);
                if (lines.size() == maxLines) {
                    lines.set(maxLines - 1, fit(lines.get(maxLines - 1) + "…", width, size));
                    return lines;
                }
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty() && lines.size() < maxLines)
            lines.add(fit(line.toString(), width, size));
        return lines;
    }

    static void icon(UiDrawList ui, String kind, float x, float y, float size, int color) {
        String icon =
                switch (kind) {
                    case "Combat" -> "swords";
                    case "Movement" -> "chevrons-up-down";
                    case "Player" -> "user-round";
                    case "Visuals" -> "eye";
                    case "UI" -> "sliders-horizontal";
                    case "Misc" -> "ellipsis";
                    case "Favorites" -> "star";
                    case "Binds" -> "keyboard";
                    case "Theme" -> "sliders-horizontal";
                    case "Search" -> "search";
                    case "Close" -> "x";
                    case "Check" -> "check";
                    case "Settings" -> "settings";
                    case "Enabled" -> "circle-check";
                    case "Collapse" -> "chevrons-up-down";
                    case "Filter" -> "sliders-horizontal";
                    default -> "ellipsis";
                };
        ui.centeredIcon(icon, x, y, size, color);
    }
}
