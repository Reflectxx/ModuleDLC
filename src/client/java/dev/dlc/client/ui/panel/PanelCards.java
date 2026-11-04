package dev.dlc.client.ui.panel;

import dev.dlc.client.render.ShaderText;
import dev.dlc.client.render.ui.AnimatedFloat;
import dev.dlc.client.render.ui.UiDrawList;
import dev.dlc.client.ui.panel.PanelModel.Module;

import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Two independently stacked lanes with shared scroll and inline, animated settings. */
final class PanelCards {
    interface Bindings {
        int key(String id);

        String label(String id);

        void set(String id, int key);
    }

    record Input(int mx, int my, boolean down, boolean pressed, boolean rightPressed) {}

    private static final int NO_POINTER = -100000;
    private final PanelSettings settings;
    private final Bindings bindings;
    private final Set<String> favorites;
    private final Map<Module, Card> states = new IdentityHashMap<>();
    private final float[] laneHeights = new float[2];
    private final float[] laneY = new float[2];
    private List<Module> visible = List.of();
    private Module pendingBind;
    private float scroll, scrollTarget, maxScroll;

    PanelCards(
            List<Module> modules,
            PanelSettings settings,
            Bindings bindings,
            Set<String> favorites) {
        this.settings = settings;
        this.bindings = bindings;
        this.favorites = favorites;
        int opened = 0;
        for (Module module : modules) {
            states.put(module, new Card(module, !module.settings.isEmpty() && opened++ < 2));
        }
    }

    void show(List<Module> modules) {
        if (visible.equals(modules)) return;
        visible = List.copyOf(modules);
        scroll = scrollTarget = 0;
        pendingBind = null;
        settings.closePopups();
        settings.releasePointer();
    }

    void syncModules(List<Module> modules) {
        Set<Module> current = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        current.addAll(modules);
        states.keySet().removeIf(module -> !current.contains(module));
        for (Module module : modules) {
            states.computeIfAbsent(
                    module,
                    value -> new Card(value, !value.settings.isEmpty() && states.size() < 2));
        }
        show(modules);
    }

    void collapseAll() {
        states.values().forEach(card -> card.expanded = false);
        resetInput();
    }

    void resetInput() {
        pendingBind = null;
        settings.closePopups();
        settings.releasePointer();
    }

    boolean binding() {
        return pendingBind != null;
    }

    boolean key(int key) {
        if (pendingBind == null) return false;
        if (key != GLFW.GLFW_KEY_ESCAPE) {
            boolean remove = key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
            bindings.set(pendingBind.id(), remove ? GLFW.GLFW_KEY_UNKNOWN : key);
        }
        pendingBind = null;
        return true;
    }

    void scrollBy(float amount) {
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget + amount));
    }

    void draw(
            UiDrawList ui,
            PanelStyle.Layout layout,
            PanelSettings.Bounds popups,
            Input input,
            boolean interactive,
            float dt,
            float opacity) {
        float s = layout.s(), width = layout.cardW();
        laneHeights[0] = laneHeights[1] = 0;
        for (int i = 0; i < visible.size(); i++) {
            Card card = states.get(visible.get(i));
            card.module.syncEnabled();
            card.prepare(width, s);
            card.open.setTarget(card.expanded ? 1 : 0);
            card.open.update(dt, 40);
            float bodyTarget = settings.heightOf(card.module, width - 20 * s, s) + 10 * s;
            card.bodyHeight += (bodyTarget - card.bodyHeight) * (1 - (float) Math.exp(-38 * dt));
            laneHeights[i % 2] += card.headerHeight + card.bodyHeight * card.open.value() + 6 * s;
        }
        maxScroll =
                Math.max(0, Math.max(laneHeights[0], laneHeights[1]) - 6 * s - layout.contentH());
        scrollBy(0);
        scroll += (scrollTarget - scroll) * (1 - (float) Math.exp(-36 * dt));
        ui.pushScissor(layout.contentX(), layout.contentY(), layout.contentW(), layout.contentH());
        laneY[0] = laneY[1] = layout.contentY() - scroll;
        for (int i = 0; i < visible.size(); i++) {
            Card card = states.get(visible.get(i));
            int lane = i % 2;
            float x = layout.contentX() + lane * (width + 6 * s);
            float base = card.headerHeight;
            float height = base + card.open.value() * card.bodyHeight;
            if (laneY[lane] + height >= layout.contentY()
                    && laneY[lane] <= layout.contentY() + layout.contentH()) {
                drawCard(
                        ui,
                        card,
                        x,
                        laneY[lane],
                        width,
                        base,
                        height,
                        s,
                        popups,
                        input,
                        interactive && layout.containsContent(input.mx(), input.my()),
                        dt,
                        opacity);
            }
            laneY[lane] += height + 6 * s;
        }
        if (visible.isEmpty()) {
            ui.centeredText(
                    layout.contentX() + layout.contentW() / 2,
                    layout.contentY() + 60 * s,
                    8 * s,
                    PanelStyle.MUTED,
                    "No modules found",
                    false);
        }
        ui.popScissor();
        if (maxScroll > s) {
            float thumb =
                    Math.max(
                            18 * s,
                            layout.contentH()
                                    * layout.contentH()
                                    / (layout.contentH() + maxScroll));
            ui.roundedRect(
                    layout.contentX() + layout.contentW() + 3 * s,
                    layout.contentY()
                            + (layout.contentH() - thumb) * Math.min(1, scroll / maxScroll),
                    1.3f * s,
                    thumb,
                    .65f * s,
                    PanelStyle.MUTED);
        }
    }

    private void drawCard(
            UiDrawList ui,
            Card card,
            float x,
            float y,
            float w,
            float base,
            float h,
            float s,
            PanelSettings.Bounds popups,
            Input input,
            boolean interactive,
            float dt,
            float opacity) {
        Module module = card.module;
        boolean hover = interactive && PanelStyle.contains(input.mx(), input.my(), x, y, w, base);
        card.hover.setTarget(hover ? 1 : 0);
        float hoverProgress = card.hover.update(dt, 36);
        ui.roundedRect(
                x,
                y,
                w,
                h,
                5 * s,
                PanelStyle.blend(PanelStyle.CARD, PanelStyle.HOVER, hoverProgress * .5f));
        ui.outlineRoundedRect(
                x,
                y,
                w,
                h,
                5 * s,
                (module.hasError() ? .9f : .5f) * s,
                module.hasError()
                        ? PanelStyle.ERROR
                        : PanelStyle.blend(PanelStyle.BORDER, PanelStyle.SELECTED, hoverProgress));
        boolean favorite = favorites.contains(module.id());
        ui.centeredIcon(
                favorite ? "star-filled" : "star",
                x + 13 * s,
                y + 14 * s,
                6.5f * s,
                favorite ? PanelStyle.FAVORITE : module.enabled ? PanelStyle.ENABLED : PanelStyle.MUTED);
        int ink = module.enabled ? PanelStyle.TEXT : PanelStyle.DISABLED_TEXT;
        ui.pushScissor(x + 22 * s, y, w - 86 * s, 26 * s);
        float nameSize = 7.2f * s;
        if (module.hasError()) {
            String suffix = " (error)";
            float nameWidth = w - 86 * s;
            float suffixWidth = ShaderText.width(suffix, nameSize);
            String name = PanelStyle.fit(module.name, nameWidth - suffixWidth, nameSize);
            ui.textCenteredVertically(x + 22 * s, y + 14 * s, nameSize, ink, name);
            ui.textCenteredVertically(
                    x + 22 * s + ShaderText.width(name, nameSize),
                    y + 14 * s,
                    nameSize,
                    PanelStyle.ERROR,
                    suffix);
        } else {
            ui.textCenteredVertically(x + 22 * s, y + 14 * s, nameSize, ink, module.name);
        }
        ui.popScissor();
        float bx = x + w - 60 * s, ex = x + w - 42 * s, tx = x + w - 25 * s;
        String bind =
                pendingBind == module
                        ? "…"
                        : bindings.key(module.id()) > 0 ? bindings.label(module.id()) : "";
        if (bind.isEmpty())
            PanelStyle.icon(ui, "Binds", bx, y + 14 * s, 6.7f * s, PanelStyle.MUTED);
        else
            ui.centeredText(
                    bx,
                    y + 14 * s,
                    5.9f * s,
                    PanelStyle.ACCENT,
                    PanelStyle.fit(bind, 17 * s, 5.9f * s),
                    false);
        PanelStyle.icon(
                ui,
                "Settings",
                ex,
                y + 14 * s,
                7 * s,
                PanelStyle.blend(PanelStyle.MUTED, PanelStyle.ACCENT, card.open.value()));
        float on = module.animation.update(dt, 40);
        ui.roundedRect(
                tx,
                y + 10.5f * s,
                13 * s,
                7 * s,
                3.5f * s,
                PanelStyle.blend(PanelStyle.DISABLED, PanelStyle.ACCENT, on));
        ui.circle(
                tx + (3.5f + 6 * on) * s,
                y + 14 * s,
                2.1f * s,
                PanelStyle.blend(PanelStyle.MUTED, PanelStyle.TEXT, on));
        int line = 0;
        for (String text : card.descriptionLines) {
            ui.text(x + 10 * s, y + (28 + line++ * 9) * s, 6.5f * s, PanelStyle.MUTED, text);
        }
        if (hover && input.pressed() && !settings.isDragging()) {
            if (input.mx() < x + 21 * s) {
                if (!favorites.add(module.id())) favorites.remove(module.id());
            } else if (Math.abs(input.mx() - bx) < 8 * s) {
                pendingBind = module;
                settings.closePopups();
            } else if (Math.abs(input.mx() - tx) < 9 * s) module.toggle();
            else if (module.settings.isEmpty()) module.toggle();
            else toggle(card);
        } else if (hover && input.rightPressed()) {
            if (module.settings.isEmpty()) module.toggle();
            else toggle(card);
        }
        float body = h - base;
        if (body <= .1f * s) return;
        ui.line(x + 10 * s, y + base, x + w - 10 * s, y + base, .5f * s, PanelStyle.BORDER);
        ui.pushScissor(x + 5 * s, y + base, w - 10 * s, body);
        ui.opacity(opacity * card.open.value());
        boolean editable = interactive && card.expanded && card.open.value() > .9f;
        settings.draw(
                ui,
                popups,
                module,
                x + 10 * s,
                y + base + 6 * s,
                w - 20 * s,
                editable ? input.mx() : NO_POINTER,
                editable ? input.my() : NO_POINTER,
                editable && input.down(),
                editable && input.pressed(),
                dt);
        ui.opacity(opacity);
        ui.popScissor();
    }

    private void toggle(Card card) {
        card.expanded = !card.expanded;
        resetInput();
    }

    private static final class Card {
        final Module module;
        final PanelStyle.Motion open = new PanelStyle.Motion(0);
        final AnimatedFloat hover = new AnimatedFloat(0);
        boolean expanded;
        private float bodyHeight;
        private List<String> descriptionLines = List.of();
        private float preparedWidth = Float.NaN;
        private float preparedScale = Float.NaN;
        private float headerHeight;

        Card(Module module, boolean expanded) {
            this.module = module;
            this.expanded = expanded;
        }

        void prepare(float width, float scale) {
            if (preparedWidth == width && preparedScale == scale) return;
            descriptionLines =
                    List.copyOf(
                            PanelStyle.wrap(
                                    module.description, width - 20 * scale, 6.5f * scale, 2));
            headerHeight = (29 + Math.max(1, descriptionLines.size()) * 9) * scale;
            preparedWidth = width;
            preparedScale = scale;
        }
    }
}
