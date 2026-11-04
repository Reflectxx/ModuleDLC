package dev.dlc.client.ui.panel;

import dev.dlc.client.render.ui.AnimatedFloat;
import dev.dlc.client.render.ui.UiDrawList;
import dev.dlc.client.ui.panel.PanelModel.Choice;
import dev.dlc.client.ui.panel.PanelModel.ColorValue;
import dev.dlc.client.ui.panel.PanelModel.Group;
import dev.dlc.client.ui.panel.PanelModel.Range;
import dev.dlc.client.ui.panel.PanelModel.Setting;
import dev.dlc.client.ui.panel.PanelModel.Slider;
import dev.dlc.client.ui.panel.PanelModel.Toggle;

import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Two-part setting rows with one shared foreground popup and captured slider input. */
final class PanelSettings {
    record Bounds(float x, float y, float w, float h, float s) {}

    private final Select select;
    private final ColorPicker colorPicker = new ColorPicker();
    private final Map<Setting, RowLayout> rowLayouts = new IdentityHashMap<>();
    private Slider dragging;
    private Range draggingRange;
    private boolean upperEndpoint;
    private float trackX, trackW;

    PanelSettings() {
        select = new Select();
    }

    PanelSettings(Select.TextWidth textWidth) {
        select = new Select(textWidth);
    }

    void beginFrame(float dt) {
        select.begin(dt);
        colorPicker.beginFrame();
    }

    boolean popupOpen() {
        return select.active() || colorPicker.active();
    }

    boolean isDragging() {
        return dragging != null || draggingRange != null;
    }

    void dismissOutside(int mx, int my, boolean pressed) {
        select.dismiss(mx, my, pressed);
        colorPicker.dismiss(mx, my, pressed);
    }

    void closePopups() {
        select.clear();
        colorPicker.close();
    }

    boolean selectKey(int key) {
        return select.key(key);
    }

    boolean selectCharacter(String value) {
        return select.character(value);
    }

    boolean selectScroll(double value) {
        return select.scroll(value);
    }

    void releasePointer() {
        dragging = null;
        draggingRange = null;
        colorPicker.releasePointer();
    }

    void advancePointer(int mx, boolean down) {
        if (!down) releasePointer();
        else if (dragging != null) {
            dragging.model.setFromMouse(mx, trackX, trackW);
            dragging.sync();
        } else if (draggingRange != null) {
            draggingRange.setFromMouse(mx, trackX, trackW, upperEndpoint);
        }
    }

    void drawPopups(UiDrawList ui, Bounds bounds, int mx, int my, boolean down, boolean pressed) {
        select.draw(ui, bounds, PanelStyle.ACCENT, mx, my, down, pressed);
        colorPicker.draw(ui, mx, my, down, pressed);
    }

    float heightOf(PanelModel.Module module, float width, float scale) {
        float height = 0;
        for (Setting setting : module.settings)
            if (setting.visible()) height += rowHeight(setting, width, scale);
        return height;
    }

    private float rowHeight(Setting setting, float width, float s) {
        return rowLayout(setting, width, s).height();
    }

    private RowLayout rowLayout(Setting setting, float width, float scale) {
        RowLayout cached = rowLayouts.get(setting);
        if (cached == null || cached.width() != width || cached.scale() != scale) {
            List<String> hints =
                    List.copyOf(PanelStyle.wrap(setting.hint(), width * .45f, 6.2f * scale, 3));
            cached =
                    new RowLayout(width, scale, hints, Math.max(28, 13 + hints.size() * 8) * scale);
            rowLayouts.put(setting, cached);
        }
        return cached;
    }

    private record RowLayout(float width, float scale, List<String> hints, float height) {}

    void draw(
            UiDrawList ui,
            Bounds bounds,
            PanelModel.Module module,
            float x,
            float y,
            float width,
            int mx,
            int my,
            boolean down,
            boolean pressed,
            float dt) {
        float s = bounds.s(), controlX = x + width * .52f, controlW = width * .48f;
        for (Setting setting : module.settings) {
            if (!setting.visible()) continue;
            RowLayout row = rowLayout(setting, width, s);
            float height = row.height();
            ui.pushScissor(x - s, y, width * .48f + s, height);
            ui.strongText(x, y + 7 * s, 7.1f * s, PanelStyle.SETTING_TEXT, setting.name());
            int line = 0;
            for (String hint : row.hints()) {
                ui.text(x, y + (17 + line++ * 8) * s, 6.2f * s, PanelStyle.MUTED, hint);
            }
            ui.popScissor();
            switch (setting) {
                case Toggle toggle -> {
                    if (pressed && hit(mx, my, controlX, y, controlW, 20 * s)) toggle.toggle();
                    float p = toggle.animation.update(dt, 40);
                    float checkSize = 8 * s;
                    float checkX = x + width - checkSize;
                    float checkY = y + 6 * s;
                    ui.roundedRect(
                            checkX,
                            checkY,
                            checkSize,
                            checkSize,
                            2.2f * s,
                            PanelStyle.blend(0xFF1B2329, PanelStyle.ACCENT, p));
                    if (p > .01f)
                        ui.centeredIcon(
                                "check",
                                checkX + checkSize / 2,
                                checkY + checkSize / 2,
                                5.5f * s,
                                PanelStyle.alpha(PanelStyle.TEXT, p));
                }
                case Slider slider -> {
                    ui.rightText(
                            x + width, y + 6 * s, 6.9f * s, PanelStyle.TEXT, slider.value(), false);
                    float ty = y + 18 * s;
                    if (pressed
                            && hit(
                                    mx,
                                    my,
                                    controlX - 12 * s,
                                    ty - 11 * s,
                                    controlW + 24 * s,
                                    24 * s)) {
                        dragging = slider;
                        draggingRange = null;
                        trackX = controlX;
                        trackW = controlW;
                        slider.model.setFromMouse(mx, trackX, trackW);
                        slider.sync();
                    }
                    float p = slider.model.update(dt);
                    ui.roundedRect(controlX, ty, controlW, 3.4f * s, 1.7f * s, 0xFF2A3035);
                    if (p > .001f)
                        ui.roundedRect(
                                controlX,
                                ty,
                                Math.max(.1f, controlW * p),
                                3.4f * s,
                                1.7f * s,
                                PanelStyle.ACCENT);
                    ui.circle(controlX + controlW * p, ty + 1.7f * s, 3 * s, PanelStyle.TEXT);
                }
                case Range range -> {
                    ui.rightText(
                            x + width, y + 6 * s, 6.9f * s, PanelStyle.TEXT, range.value(), false);
                    float ty = y + 18 * s;
                    if (pressed
                            && hit(
                                    mx,
                                    my,
                                    controlX - 12 * s,
                                    ty - 11 * s,
                                    controlW + 24 * s,
                                    24 * s)) {
                        dragging = null;
                        draggingRange = range;
                        trackX = controlX;
                        trackW = controlW;
                        float lowX = controlX + range.lowProgress.value() * controlW;
                        float highX = controlX + range.highProgress.value() * controlW;
                        upperEndpoint = Math.abs(mx - highX) <= Math.abs(mx - lowX);
                        range.setFromMouse(mx, trackX, trackW, upperEndpoint);
                    }
                    range.update(dt);
                    float lowX = controlX + range.lowProgress.value() * controlW;
                    float highX = controlX + range.highProgress.value() * controlW;
                    ui.roundedRect(controlX, ty, controlW, 3.4f * s, 1.7f * s, 0xFF2A3035);
                    if (highX - lowX > .01f * s) {
                        ui.roundedRect(
                                lowX, ty, highX - lowX, 3.4f * s, 1.7f * s, PanelStyle.ACCENT);
                    }
                    ui.circle(lowX, ty + 1.7f * s, 3 * s, PanelStyle.TEXT);
                    ui.circle(highX, ty + 1.7f * s, 3 * s, PanelStyle.TEXT);
                }
                case Choice choice ->
                        select.trigger(
                                ui,
                                choice,
                                controlX,
                                y + 2 * s,
                                controlW,
                                s,
                                PanelStyle.ACCENT,
                                mx,
                                my,
                                pressed);
                case Group group ->
                        select.trigger(
                                ui,
                                group,
                                controlX,
                                y + 2 * s,
                                controlW,
                                s,
                                PanelStyle.ACCENT,
                                mx,
                                my,
                                pressed);
                case ColorValue color -> {
                    if (colorPicker.trigger(
                            ui, bounds, color, controlX, y, controlW, mx, my, pressed))
                        select.clear();
                }
            }
            y += height;
        }
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return PanelStyle.contains(mx, my, x, y, w, h);
    }

    /** One floating menu with captured selection, keyboard navigation and viewport fitting. */
    static final class Select {
        private static final int WHITE = 0xFFF5F5F5;
        private static final int ROW_PITCH = 17;
        private static final int TRIGGER_HEIGHT = 18;
        private final Map<Setting, Entry> entries = new IdentityHashMap<>();
        private final TextWidth textWidth;
        private Entry menu;
        private Entry focus;
        private boolean expanded;
        private boolean captured;
        private int frame;
        private int lastMouseX = Integer.MIN_VALUE;
        private int lastMouseY = Integer.MIN_VALUE;
        private float scroll;
        private float scrollTarget;
        private MenuBounds geometry;

        @FunctionalInterface
        interface TextWidth {
            float measure(String text, float size);
        }

        Select() {
            this((text, size) -> text.codePointCount(0, text.length()) * size * .6f);
        }

        Select(TextWidth textWidth) {
            this.textWidth = java.util.Objects.requireNonNull(textWidth);
        }

        private int pitch() {
            return ROW_PITCH;
        }

        private int rowHeight() {
            return pitch();
        }

        boolean active() {
            return menu != null;
        }

        void begin(float dt) {
            frame++;
            for (Entry entry : entries.values()) {
                entry.open.setTarget(menu == entry && expanded ? 1 : 0);
                    entry.open.update(dt, expanded ? 44 : 56);
                    entry.pill.update(dt, 40);
            }
            scroll += (scrollTarget - scroll) * (1 - (float) Math.exp(-36 * dt));
            if (menu != null && menu.seen < frame - 1) clear();
            else if (menu != null && !expanded && menu.open.value() < .008f) {
                menu = null;
                captured = false;
                geometry = null;
                scroll = scrollTarget = 0;
            }
            if (focus != null && focus.seen < frame - 1) focus = null;
        }

        void dismiss(int mx, int my, boolean pressed) {
            if (!pressed) return;
            if (menu != null
                    && !menu.contains(mx, my)
                    && (geometry == null || !geometry.contains(mx, my))) {
                focus = null;
                if (expanded) close();
            } else if (menu == null && focus != null && !focus.contains(mx, my)) focus = null;
        }

        void clear() {
            menu = null;
            expanded = captured = false;
            geometry = null;
            scroll = scrollTarget = 0;
            focus = null;
        }

        private void close() {
            expanded = captured = false;
        }

        private void open(Entry entry) {
            menu = focus = entry;
            expanded = true;
            captured = false;
            scroll = scrollTarget = 0;
            geometry = null;
            entry.active = entry.selected();
            entry.pill = new AnimatedFloat(entry.active);
        }

        void trigger(
                UiDrawList ui,
                Setting setting,
                float x,
                float y,
                float w,
                float s,
                int accent,
                int mx,
                int my,
                boolean pressed) {
            Entry entry = entries.computeIfAbsent(setting, Entry::new);
            entry.x = x;
            entry.y = y;
            entry.w = w;
            entry.s = s;
            entry.seen = frame;
            entry.triggerH = TRIGGER_HEIGHT;
            boolean over = entry.contains(mx, my);
            if (pressed && over && entry.count() > 0) {
                entry.triggerFrame = frame;
                if (menu == entry && expanded) close();
                else open(entry);
            }
            float p = entry.open.value(), h = entry.triggerH * s, radius = 3 * s;
            ui.roundedRect(x, y, w, h, radius, over ? 0xFF242A30 : 0xFF1B2228);
            ui.outlineRoundedRect(x, y, w, h, radius, .5f * s, 0xFF252F37);
            if (menu == entry && p > .005f) {
                boolean above = geometry != null && geometry.up();
                ui.rect(
                        x + .5f * s,
                        above ? y : y + h - 3 * s,
                        w - s,
                        3 * s,
                        over ? 0xFF242A30 : 0xFF1B2228);
            }
            // The clip starts before the ink. Chosen values remain sharp during selection.
            float textX = x + 6 * s;
            float textWidth = w - 25 * s;
            ui.pushScissor(x + 4 * s, y, w - 23 * s, h);
            String label = entry.value();
            if (setting instanceof Group group) {
                label =
                        group.values.stream()
                                .filter(option -> option.value)
                                .map(option -> option.name())
                                .collect(java.util.stream.Collectors.joining(", "));
                if (label.isBlank()) {
                    label = "None";
                }
            }
            ui.textCenteredVertically(
                    textX, y + h / 2, 6.8f * s, WHITE, entry.fitLabel(label, textWidth, 6.8f * s));
            ui.popScissor();
            ui.rotatedCenteredIcon(
                    "chevron-down",
                    x + w - 12 * s,
                    y + h / 2,
                    7 * s,
                    0xFFAAB7BE,
                    p * (float) Math.PI);
        }

        void draw(
                UiDrawList ui,
                PanelSettings.Bounds bounds,
                int accent,
                int mx,
                int my,
                boolean down,
                boolean pressed) {
            if (menu == null || menu.seen != frame) return;
            Entry entry = menu;
            boolean fresh = geometry == null;
            geometry = MenuBounds.create(entry.x, entry.y, entry.w, entry.s, entry.count(), bounds);
            if (fresh) {
                ensureVisible();
                scroll = scrollTarget;
            }
            float p = entry.open.value();
            // Trigger input is handled here too: base settings input is locked while a popup is
            // alive.
            if (pressed && entry.triggerFrame != frame && entry.contains(mx, my)) {
                if (expanded) close();
                else expanded = true;
            }
            if (p < .005f) return;
            // Reveal only height. Labels, checkbox sizes and menu width never scale or blur.
            float w = geometry.w, h = geometry.h, s = entry.s;
            float x = geometry.x, y = geometry.y;
            float reveal = Math.max(.1f * s, h * p);
            float revealY = geometry.up ? y + h - reveal : y;
            float row = pitch() * s, pad = 3 * s, view = h - 6 * s, rh = rowHeight() * s;
            float maxScroll = Math.max(0, entry.count() * row - view);
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            int hovered = -1;
            if (hit(mx, my, x, revealY, w, reveal)
                    && hit(mx, my, x + pad, y + pad, w - pad * 2, view)) {
                int index = (int) ((my - y - pad + scroll) / row);
                if (index >= 0 && index < entry.count() && (my - y - pad + scroll) % row < rh)
                    hovered = index;
            }
            boolean moved = mx != lastMouseX || my != lastMouseY;
            lastMouseX = mx;
            lastMouseY = my;
            if (expanded && p > .65f) {
                if (pressed && hovered >= 0) captured = true;
                if (hovered >= 0 && (moved || captured)) {
                    entry.active = hovered;
                    entry.pill.setTarget(hovered);
                }
                if (captured && !down) {
                    captured = false;
                    if (hovered >= 0) pick(hovered);
                }
            }
            float radius = 3 * s;
            ui.pushScissor(x, revealY, w, reveal);
            ui.roundedRect(x, y, w, h, radius, alpha(0xFF1B2228, p));
            ui.outlineRoundedRect(x, y, w, h, radius, .5f * s, alpha(0xFF252F37, p));
            ui.pushScissor(x + pad, y + pad, w - pad * 2, view);
            ui.roundedRect(
                    x + pad,
                    y + pad + entry.pill.value() * row - scroll,
                    w - pad * 2,
                    rh,
                    2 * s,
                    alpha(0xFF293139, p));
            for (int i = 0; i < entry.count(); i++) {
                float ry = y + pad + i * row - scroll;
                if (ry + rh < y + pad || ry > y + h - pad) continue;
                boolean checked = entry.checked(i);
                float checkboxX = x + 6 * s, checkboxY = ry + rh / 2 - 3 * s;
                ui.roundedRect(
                        checkboxX,
                        checkboxY,
                        6 * s,
                        6 * s,
                        1.3f * s,
                        alpha(checked ? accent : 0xFF21282E, p));
                if (checked) {
                    PanelStyle.icon(
                            ui,
                            "Check",
                            checkboxX + 3 * s,
                            checkboxY + 3 * s,
                            4.5f * s,
                            alpha(WHITE, p));
                } else {
                    ui.outlineRoundedRect(
                            checkboxX,
                            checkboxY,
                            6 * s,
                            6 * s,
                            1.3f * s,
                            .5f * s,
                            alpha(0xFF303941, p));
                }
                ui.pushScissor(x + 16 * s, ry, w - 22 * s, rh);
                ui.textCenteredVertically(
                        x + 17 * s,
                        ry + rh / 2,
                        6.2f * s,
                        alpha(checked ? WHITE : PanelStyle.MUTED, p),
                        entry.fitOption(i, w - 24 * s, 6.2f * s));
                ui.popScissor();
            }
            ui.popScissor();
            if (maxScroll > s) {
                float thumb = Math.max(16 * s, view * view / (view + maxScroll));
                ui.roundedRect(
                        x + w - 3 * s,
                        y + pad + (view - thumb) * scroll / maxScroll,
                        1.3f * s,
                        thumb,
                        s,
                        alpha(accent, p * .5f));
            }
            ui.popScissor();
        }

        private void pick(int index) {
            if (menu == null || index < 0 || index >= menu.count()) return;
            Entry entry = menu;
            if (entry.setting instanceof Choice choice) {
                choice.select(index);
                close();
            } else if (entry.setting instanceof Group group) {
                group.values.get(index).toggle();
            }
        }

        boolean key(int key) {
            if (menu == null) {
                if (focus != null
                        && (key == GLFW.GLFW_KEY_ENTER
                                || key == GLFW.GLFW_KEY_SPACE
                                || key == GLFW.GLFW_KEY_DOWN
                                || key == GLFW.GLFW_KEY_UP)) {
                    open(focus);
                    return true;
                }
                return false;
            }
            if (!expanded) return true;
            int next = menu.active;
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> close();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> pick(next);
                case GLFW.GLFW_KEY_DOWN -> next++;
                case GLFW.GLFW_KEY_UP -> next--;
                case GLFW.GLFW_KEY_HOME -> next = 0;
                case GLFW.GLFW_KEY_END -> next = menu.count() - 1;
                default -> {
                    return true;
                }
            }
            if (menu != null) {
                menu.active = Math.max(0, Math.min(menu.count() - 1, next));
                menu.pill.setTarget(menu.active);
                ensureVisible();
            }
            return true;
        }

        boolean character(String value) {
            if (menu == null || !expanded) return false;
            String prefix = value.toLowerCase(Locale.ROOT);
            for (int offset = 1; offset <= menu.count(); offset++) {
                int index = (menu.active + offset) % menu.count();
                if (menu.label(index).toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    menu.active = index;
                    menu.pill.setTarget(index);
                    ensureVisible();
                    break;
                }
            }
            return true;
        }

        boolean scroll(double amount) {
            if (menu == null) return false;
            if (expanded) {
                scrollTarget -= (float) amount * pitch() * menu.s;
                if (geometry != null)
                    scrollTarget =
                            Math.max(
                                    0,
                                    Math.min(
                                            Math.max(
                                                    0,
                                                    menu.count() * pitch() * menu.s
                                                            - (geometry.h - 6 * menu.s)),
                                            scrollTarget));
            }
            return true;
        }

        private void ensureVisible() {
            if (geometry == null || menu == null) return;
            float row = pitch() * menu.s, view = geometry.h - 6 * menu.s, pos = menu.active * row;
            if (pos < scrollTarget) scrollTarget = pos;
            else if (pos + rowHeight() * menu.s > scrollTarget + view)
                scrollTarget = pos + rowHeight() * menu.s - view;
        }

        private String fitLabel(String label, float width, float fontSize) {
            if (textWidth.measure(label, fontSize) <= width) return label;
            String ellipsis = "…";
            if (textWidth.measure(ellipsis, fontSize) > width) return "";
            int low = 0, high = label.codePointCount(0, label.length());
            while (low < high) {
                int middle = (low + high + 1) / 2;
                String candidate =
                        label.substring(0, label.offsetByCodePoints(0, middle)) + ellipsis;
                if (textWidth.measure(candidate, fontSize) <= width) low = middle;
                else high = middle - 1;
            }
            return label.substring(0, label.offsetByCodePoints(0, low)) + ellipsis;
        }

        private static int alpha(int color, float p) {
            return color & 0xFFFFFF
                    | Math.round((color >>> 24) * Math.max(0, Math.min(1, p))) << 24;
        }

        private static boolean hit(float mx, float my, float x, float y, float w, float h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        private final class Entry {
            final Setting setting;
            final PanelStyle.Motion open = new PanelStyle.Motion(0);
            AnimatedFloat pill = new AnimatedFloat(0);
            float x, y, w, s = 1;
            int active, seen, triggerFrame = -1, triggerH = TRIGGER_HEIGHT;
            String measuredLabel;
            String fittedLabel;
            float measuredWidth, measuredSize;
            String[] fittedOptions;
            float optionWidth, optionSize;

            Entry(Setting setting) {
                this.setting = setting;
            }

            String fitLabel(String label, float width, float fontSize) {
                if (!label.equals(measuredLabel)
                        || width != measuredWidth
                        || fontSize != measuredSize) {
                    measuredLabel = label;
                    measuredWidth = width;
                    measuredSize = fontSize;
                    fittedLabel = PanelSettings.Select.this.fitLabel(label, width, fontSize);
                }
                return fittedLabel;
            }

            String fitOption(int index, float width, float size) {
                if (fittedOptions == null
                        || fittedOptions.length != count()
                        || optionWidth != width
                        || optionSize != size) {
                    fittedOptions = new String[count()];
                    optionWidth = width;
                    optionSize = size;
                }
                if (fittedOptions[index] == null)
                    fittedOptions[index] = Select.this.fitLabel(label(index), width, size);
                return fittedOptions[index];
            }

            int count() {
                return setting instanceof Choice c
                        ? c.values.size()
                        : ((Group) setting).values.size();
            }

            int selected() {
                return setting instanceof Choice c ? Math.max(0, c.selected) : 0;
            }

            String value() {
                return setting instanceof Choice c ? c.value() : ((Group) setting).summary();
            }

            String label(int index) {
                return setting instanceof Choice c
                        ? c.values.get(index)
                        : ((Group) setting).values.get(index).name();
            }

            boolean checked(int index) {
                return setting instanceof Choice c
                        ? c.selected == index
                        : ((Group) setting).values.get(index).value;
            }

            boolean contains(float mx, float my) {
                return hit(mx, my, x, y, w, triggerH * s);
            }
        }

        record MenuBounds(float x, float y, float w, float h, boolean up) {
            static MenuBounds create(
                    float x,
                    float y,
                    float triggerW,
                    float s,
                    int count,
                    PanelSettings.Bounds bounds) {
                int offset = TRIGGER_HEIGHT - 1;
                float bottom = bounds.y() + bounds.h() - 4 * s, top = bounds.y() + 4 * s;
                float below = Math.max(0, bottom - y - offset * s),
                        above = Math.max(0, y + s - top);
                float need = (count * ROW_PITCH + 6) * s;
                boolean up = below < need && above > below;
                float h = Math.max(9 * s, Math.min(need, Math.max(9 * s, up ? above : below)));
                float w = Math.min(triggerW, bounds.w() - 8 * s);
                float px =
                        Math.max(
                                bounds.x() + 4 * s,
                                Math.min(bounds.x() + bounds.w() - w - 4 * s, x));
                float py = up ? y + s - h : y + offset * s;
                py = Math.max(top, Math.min(bottom - h, py));
                return new MenuBounds(px, py, w, h, up);
            }

            boolean contains(float mx, float my) {
                return hit(mx, my, x, y, w, h);
            }
        }
    }

    /** One captured HSV/alpha popup shared by all inline color controls. */
    static final class ColorPicker {
        private static final int[] HUE_COLORS = {
            0xFFFF4D4D, 0xFFFFFF4D, 0xFF4DFF70, 0xFF4DE8FF, 0xFF596BFF, 0xFFC44DFF, 0xFFFF4D7F
        };
        private ColorValue selected;
        private Area geometry;
        private Area trigger;
        private Drag drag;
        private int frame;
        private int seen;

        void beginFrame() {
            frame++;
            if (selected != null && seen < frame - 1) close();
        }

        boolean active() {
            return selected != null && geometry != null;
        }

        void close() {
            selected = null;
            geometry = null;
            trigger = null;
            drag = null;
        }

        void releasePointer() {
            drag = null;
        }

        void dismiss(int mouseX, int mouseY, boolean pressed) {
            if (pressed
                    && active()
                    && !geometry.contains(mouseX, mouseY)
                    && (trigger == null || !trigger.contains(mouseX, mouseY))) close();
        }

        boolean trigger(
                UiDrawList ui,
                PanelSettings.Bounds bounds,
                ColorValue color,
                float x,
                float y,
                float width,
                int mouseX,
                int mouseY,
                boolean pressed) {
            float scale = bounds.s();
            ui.rightText(
                    x + width - 14 * scale,
                    y + 7 * scale,
                    6.6f * scale,
                    PanelStyle.TEXT,
                    color.value(),
                    false);
            ui.circle(x + width - 4 * scale, y + 7 * scale, 3.8f * scale, color.argb());
            Area field = new Area(x, y, width, 20 * scale, scale);
            boolean clicked = pressed && field.contains(mouseX, mouseY);
            if (clicked) {
                if (selected == color) close();
                else {
                    selected = color;
                    drag = null;
                }
            }
            if (selected == color) {
                seen = frame;
                trigger = field;
                float pickerWidth = 126 * scale;
                float pickerHeight = 105 * scale;
                float pickerX =
                        Math.max(
                                bounds.x(),
                                Math.min(
                                        bounds.x() + bounds.w() - pickerWidth,
                                        x + width - pickerWidth));
                float pickerY =
                        Math.max(
                                bounds.y(),
                                Math.min(bounds.y() + bounds.h() - pickerHeight, y + 17 * scale));
                geometry = new Area(pickerX, pickerY, pickerWidth, pickerHeight, scale);
            }
            return clicked;
        }

        void draw(UiDrawList ui, int mouseX, int mouseY, boolean down, boolean pressed) {
            if (!active() || seen != frame) return;
            Area panel = geometry;
            float scale = panel.scale;
            Area saturation =
                    new Area(
                            panel.x + 7 * scale,
                            panel.y + 17 * scale,
                            panel.width - 14 * scale,
                            46 * scale,
                            scale);
            Area hue =
                    new Area(
                            saturation.x, panel.y + 69 * scale, saturation.width, 7 * scale, scale);
            Area alpha =
                    new Area(
                            saturation.x, panel.y + 82 * scale, saturation.width, 7 * scale, scale);
            if (pressed) {
                if (saturation.contains(mouseX, mouseY)) drag = Drag.SATURATION;
                else if (hue.contains(mouseX, mouseY)) drag = Drag.HUE;
                else if (alpha.contains(mouseX, mouseY)) drag = Drag.ALPHA;
            }
            if (down && drag != null) updateColor(saturation, hue, alpha, mouseX, mouseY);
            ui.roundedRect(panel.x, panel.y, panel.width, panel.height, 5 * scale, 0xFF171717);
            ui.outlineRoundedRect(
                    panel.x,
                    panel.y,
                    panel.width,
                    panel.height,
                    5 * scale,
                    .6f * scale,
                    0xFF39454B);
            ui.text(
                    panel.x + 7 * scale,
                    panel.y + 9 * scale,
                    6.5f * scale,
                    PanelStyle.TEXT,
                    "Color");
            ui.rightText(
                    panel.x + panel.width - 7 * scale,
                    panel.y + 9 * scale,
                    6 * scale,
                    PanelStyle.SETTING_TEXT,
                    selected.value(),
                    false);
            drawSaturation(ui, saturation);
            drawHue(ui, hue);
            drawAlpha(ui, alpha);
            ui.text(saturation.x, panel.y + 97 * scale, 5.6f * scale, PanelStyle.MUTED, "Opacity");
            ui.rightText(
                    saturation.x + saturation.width,
                    panel.y + 97 * scale,
                    5.6f * scale,
                    PanelStyle.SETTING_TEXT,
                    Math.round(selected.alpha() * 100) + "%",
                    false);
        }

        private void updateColor(Area saturation, Area hue, Area alpha, int mouseX, int mouseY) {
            switch (drag) {
                case SATURATION ->
                        selected.setHsb(
                                selected.hue(),
                                unit((mouseX - saturation.x) / saturation.width),
                                1 - unit((mouseY - saturation.y) / saturation.height));
                case HUE ->
                        selected.setHsb(
                                unit((mouseX - hue.x) / hue.width),
                                selected.saturation(),
                                selected.brightness());
                case ALPHA -> selected.setAlpha(unit((mouseX - alpha.x) / alpha.width));
            }
        }

        private void drawSaturation(UiDrawList ui, Area area) {
            int color = 0xFF000000 | (java.awt.Color.HSBtoRGB(selected.hue(), 1, 1) & 0xFFFFFF);
            ui.roundedRect(area.x, area.y, area.width, area.height, 3 * area.scale, color);
            ui.gradientRoundedRect(
                    area.x,
                    area.y,
                    area.width,
                    area.height,
                    3 * area.scale,
                    0xFFFFFFFF,
                    0x00FFFFFF,
                    true);
            ui.gradientRoundedRect(
                    area.x, area.y, area.width, area.height, 3 * area.scale, 0, 0xFF000000, false);
            float x = area.x + area.width * selected.saturation();
            float y = area.y + area.height * (1 - selected.brightness());
            ui.circle(x, y, 3 * area.scale, PanelStyle.TEXT);
            ui.circle(x, y, 1.8f * area.scale, 0xFF000000 | (selected.argb() & 0xFFFFFF));
        }

        private void drawHue(UiDrawList ui, Area area) {
            for (int i = 0; i < HUE_COLORS.length - 1; i++) {
                float width = area.width / (HUE_COLORS.length - 1);
                ui.gradientRoundedRect(
                        area.x + i * width,
                        area.y,
                        width + .5f,
                        area.height,
                        0,
                        HUE_COLORS[i],
                        HUE_COLORS[i + 1],
                        true);
            }
            float x = area.x + area.width * selected.hue();
            ui.roundedRect(
                    x - 1.2f * area.scale,
                    area.y - 1.5f * area.scale,
                    2.4f * area.scale,
                    area.height + 3 * area.scale,
                    1.2f * area.scale,
                    PanelStyle.TEXT);
        }

        private void drawAlpha(UiDrawList ui, Area area) {
            float tile = 3.5f * area.scale;
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < Math.ceil(area.width / tile); column++) {
                    ui.rect(
                            area.x + column * tile,
                            area.y + row * tile,
                            Math.min(tile, area.width - column * tile),
                            Math.min(tile, area.height - row * tile),
                            ((row + column) & 1) == 0 ? 0xFF34363D : 0xFF1E2026);
                }
            }
            int rgb = selected.argb() & 0xFFFFFF;
            ui.gradientRoundedRect(
                    area.x,
                    area.y,
                    area.width,
                    area.height,
                    3 * area.scale,
                    rgb,
                    0xFF000000 | rgb,
                    true);
            float x = area.x + area.width * selected.alpha();
            ui.roundedRect(
                    x - 1.2f * area.scale,
                    area.y - 1.5f * area.scale,
                    2.4f * area.scale,
                    area.height + 3 * area.scale,
                    1.2f * area.scale,
                    PanelStyle.TEXT);
        }

        private static float unit(float value) {
            return Math.max(0, Math.min(1, value));
        }

        private record Area(float x, float y, float width, float height, float scale) {
            boolean contains(float mouseX, float mouseY) {
                return PanelStyle.contains(mouseX, mouseY, x, y, width, height);
            }
        }

        private enum Drag {
            SATURATION,
            HUE,
            ALPHA
        }
    }
}
