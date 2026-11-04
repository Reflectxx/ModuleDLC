package dev.dlc.client.ui.panel;

import dev.dlc.client.ClientCore;
import dev.dlc.client.DiscordRpcManager;
import dev.dlc.client.render.ui.AnimatedFloat;
import dev.dlc.client.render.ui.UiDrawList;
import dev.dlc.client.ui.CsKeybindManager;
import dev.dlc.client.ui.panel.PanelModel.Category;
import dev.dlc.client.ui.panel.PanelModel.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Compact sidebar screen. All controls edit live client modules, not screenshot placeholders. */
public final class Panel extends Screen {
    private enum View {
        CATEGORY,
        FAVORITES,
        BINDS,
        THEME
    }

    private static final Set<String> FAVORITES = new HashSet<>();
    private List<Category> categories;
    private List<Module> all;
    private final PanelSettings settings =
            new PanelSettings(
                    (text, size) ->
                            ClientCore.getInstance().renderer().textWidth(text, size, false));
    private final PanelCards.Bindings binds =
            new PanelCards.Bindings() {
                public int key(String id) {
                    return CsKeybindManager.get().binding(id);
                }

                public String label(String id) {
                    return CsKeybindManager.get().label(id);
                }

                public void set(String id, int key) {
                    CsKeybindManager.get().bind(id, key);
                }
            };
    private final PanelCards cards;
    private final PanelStyle.Motion entrance = new PanelStyle.Motion(0);
    private final Map<String, AnimatedFloat> navMotion = new HashMap<>();
    private String category = "Combat";
    private String search = "";
    private View view = View.CATEGORY;
    private boolean enabledOnly, searchFocused, closing, previousLeft, previousRight;
    private boolean pendingLeftClick, pendingRightClick;
    private int pendingClickX, pendingClickY;
    private long lastFrame = System.nanoTime();
    private PanelStyle.Layout layout;
    private long moduleRevision;
    private float themeScroll, themeMaxScroll;

    public Panel() {
        super(Component.literal(ClientCore.NAME));
        categories = PanelModel.create();
        all = categories.stream().flatMap(category -> category.modules().stream()).toList();
        cards = new PanelCards(all, settings, binds, FAVORITES);
        moduleRevision = ClientCore.getInstance().modules().revision();
        entrance.setTarget(1);
        previousLeft = button(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        previousRight = button(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        try {
        refreshModules();
        long now = System.nanoTime();
        float dt = Math.max(0, Math.min(.05f, (now - lastFrame) / 1_000_000_000f));
        lastFrame = now;
        float opacity = entrance.update(dt, closing ? 48 : 38);
        if (closing && opacity < .01f) {
            super.onClose();
            return;
        }
        boolean down = button(GLFW.GLFW_MOUSE_BUTTON_LEFT),
                right = button(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean pressed = pendingLeftClick || down && !previousLeft;
        boolean rightPressed = pendingRightClick || right && !previousRight;
        int inputX = pendingLeftClick || pendingRightClick ? pendingClickX : mx;
        int inputY = pendingLeftClick || pendingRightClick ? pendingClickY : my;
        pendingLeftClick = false;
        pendingRightClick = false;
        int clickX = pressed ? inputX : mx;
        int clickY = pressed ? inputY : my;
        PanelCards.Input input =
                new PanelCards.Input(inputX, inputY, down, pressed, rightPressed);
        layout = PanelStyle.Layout.create(width, height, opacity);
        boolean locked = settings.popupOpen();
        settings.advancePointer(mx, down && !closing);
        settings.dismissOutside(clickX, clickY, input.pressed());
        settings.beginFrame(dt);
        boolean interactive = !closing && opacity > .9f && !locked;
        UiDrawList ui = new UiDrawList(graphics);
        ui.rect(0, 0, width, height, PanelStyle.alpha(PanelStyle.SHADOW, opacity));
        ui.opacity(opacity);
        ui.roundedRect(
                layout.x(), layout.y(), layout.w(), layout.h(), 9 * layout.s(), PanelStyle.PANEL);
        ui.outlineRoundedRect(
                layout.x(),
                layout.y(),
                layout.w(),
                layout.h(),
                9 * layout.s(),
                .6f * layout.s(),
                PanelStyle.BORDER);
        drawHeader(ui, input, interactive);
        drawSidebar(ui, input, interactive, dt);
        cards.show(view == View.THEME ? List.of() : filtered());
        var bounds =
                new PanelSettings.Bounds(
                        layout.x() + 4 * layout.s(),
                        layout.y() + 4 * layout.s(),
                        layout.w() - 8 * layout.s(),
                        layout.h() - 8 * layout.s(),
                        layout.s());
        if (view == View.THEME) {
            drawThemes(ui, input, interactive, dt);
        } else {
            cards.draw(ui, layout, bounds, input, interactive && !cards.binding(), dt, opacity);
        }
        settings.drawPopups(
                ui,
                bounds,
                clickX,
                clickY,
                down && !closing,
                input.pressed() && !closing);
        ui.opacity(1);
        if (!down) settings.releasePointer();
        previousLeft = down;
        previousRight = right;
        } catch (Throwable t) {
            dev.dlc.ModuleDLC.LOGGER.error("Panel render failed", t);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            pendingClickX = (int) event.x();
            pendingClickY = (int) event.y();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pendingLeftClick = true;
            else pendingRightClick = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void refreshModules() {
        long revision = ClientCore.getInstance().modules().revision();
        if (revision == moduleRevision) return;
        categories = PanelModel.create();
        all = categories.stream().flatMap(category -> category.modules().stream()).toList();
        cards.syncModules(all);
        if (categories.stream().noneMatch(item -> item.name().equals(category))) {
            category = categories.getFirst().name();
        }
        moduleRevision = revision;
    }

    private List<Module> filtered() {
        List<Module> source =
                view == View.CATEGORY && search.isBlank()
                        ? categories.stream()
                                .filter(item -> item.name().equals(category))
                                .findFirst()
                                .orElseThrow()
                                .modules()
                        : all;
        String needle = search.toLowerCase(Locale.ROOT).strip();
        return source.stream()
                .filter(module -> !enabledOnly || module.enabled)
                .filter(module -> view != View.FAVORITES || FAVORITES.contains(module.id()))
                .filter(module -> view != View.BINDS || binds.key(module.id()) > 0)
                .filter(module -> needle.isEmpty() || module.searchText.contains(needle))
                .toList();
    }

    private void drawHeader(UiDrawList ui, PanelCards.Input input, boolean interactive) {
        float s = layout.s(), x = layout.x(), y = layout.y();
        ui.flameIcon(x + 10 * s, y + 10 * s, 12 * s, PanelStyle.ACCENT);
        ui.textCenteredVertically(
                x + 25 * s, y + 16 * s, 8 * s, PanelStyle.TEXT, ClientCore.NAME);
        ui.line(x + s, y + 32 * s, x + layout.w() - s, y + 32 * s, .5f * s, PanelStyle.BORDER);
        boolean hover =
                PanelStyle.contains(
                        input.mx(), input.my(), x + layout.w() - 25 * s, y + 5 * s, 20 * s, 22 * s);
        PanelStyle.icon(
                ui,
                "Close",
                x + layout.w() - 13 * s,
                y + 16 * s,
                7 * s,
                hover ? PanelStyle.TEXT : PanelStyle.SECTION_TEXT);
        if (interactive && hover && input.pressed()) onClose();
    }

    private void drawSidebar(UiDrawList ui, PanelCards.Input input, boolean interactive, float dt) {
        float s = layout.s(), x = layout.x() + 10 * s, w = layout.sidebar() - 20 * s;
        ui.line(
                layout.x() + layout.sidebar(),
                layout.y() + 32 * s,
                layout.x() + layout.sidebar(),
                layout.y() + layout.h() - 10 * s,
                .5f * s,
                PanelStyle.BORDER);
        ui.text(x, layout.y() + 47 * s, 6.3f * s, PanelStyle.SECTION_TEXT, "Main");
        float y = layout.y() + 56 * s;
        for (Category item : categories) {
            boolean selected =
                    view == View.CATEGORY && category.equals(item.name()) && search.isBlank();
            if (navigation(ui, input, interactive, item.name(), x, y, w, selected, dt)) {
                category = item.name();
                view = View.CATEGORY;
                search = "";
                searchFocused = false;
                cards.resetInput();
            }
            y += 25 * s;
        }
        ui.text(x, y + 12 * s, 6.3f * s, PanelStyle.SECTION_TEXT, "Other");
        y += 21 * s;
        if (navigation(ui, input, interactive, "Favorites", x, y, w, view == View.FAVORITES, dt)) {
            view = View.FAVORITES;
            searchFocused = false;
            cards.resetInput();
        }
        if (navigation(ui, input, interactive, "Binds", x, y + 25 * s, w, view == View.BINDS, dt)) {
            view = View.BINDS;
            searchFocused = false;
            cards.resetInput();
        }
        if (navigation(ui, input, interactive, "Theme", x, y + 50 * s, w, view == View.THEME, dt)) {
            view = View.THEME;
            searchFocused = false;
            cards.resetInput();
        }
        float sy = layout.y() + layout.h() - 59 * s;
        boolean searchHover = PanelStyle.contains(input.mx(), input.my(), x, sy, w, 24 * s);
        if (interactive && input.pressed()) searchFocused = searchHover;
        ui.roundedRect(x, sy, w, 24 * s, 4 * s, PanelStyle.CARD);
        ui.outlineRoundedRect(
                x, sy, w, 24 * s, 4 * s, .5f * s, searchFocused ? PanelStyle.ACCENT : PanelStyle.BORDER);
        PanelStyle.icon(ui, "Search", x + 11 * s, sy + 12 * s, 7 * s, PanelStyle.MUTED);
        ui.pushScissor(x + 20 * s, sy, w - 26 * s, 24 * s);
        ui.textCenteredVertically(
                x + 21 * s,
                sy + 12 * s,
                6.9f * s,
                search.isEmpty() ? PanelStyle.MUTED : PanelStyle.TEXT,
                search.isEmpty() ? "Search..." : PanelStyle.fit(search, w - 26 * s, 6.9f * s));
        if (searchFocused && (System.nanoTime() / 500_000_000L) % 2 == 0) {
            float cursorX =
                    x
                            + 21 * s
                            + Math.min(
                                    w - 29 * s,
                                    ClientCore.getInstance()
                                            .renderer()
                                            .textWidth(search, 6.9f * s, false));
            ui.line(cursorX, sy + 8 * s, cursorX, sy + 16 * s, .6f * s, PanelStyle.ACCENT);
        }
        ui.popScissor();
        drawProfile(ui, x, sy + 26 * s, w, 23 * s, s);
    }

    private void drawThemes(
            UiDrawList ui, PanelCards.Input input, boolean interactive, float dt) {
        String needle = search.toLowerCase(Locale.ROOT).strip();
        List<ThemeManager.Theme> themes =
                ThemeManager.get().themes().stream()
                        .filter(
                                theme ->
                                        needle.isEmpty()
                                                || theme.name().toLowerCase(Locale.ROOT).contains(needle)
                                                || theme.colors().keySet().stream()
                                                        .anyMatch(
                                                                role ->
                                                                        role.toLowerCase(Locale.ROOT)
                                                                                .contains(needle)))
                        .toList();
        float s = layout.s();
        float width = layout.contentW();
        float cardHeight = 48 * s;
        float gap = 5 * s;
        int rows = themes.size();
        themeMaxScroll = Math.max(0, rows * (cardHeight + gap) - gap - layout.contentH());
        themeScroll = Math.max(0, Math.min(themeMaxScroll, themeScroll));
        ui.pushScissor(layout.contentX(), layout.contentY(), layout.contentW(), layout.contentH());
        for (int i = 0; i < themes.size(); i++) {
            ThemeManager.Theme theme = themes.get(i);
            float x = layout.contentX();
            float y = layout.contentY() + i * (cardHeight + gap) - themeScroll;
            if (y + cardHeight < layout.contentY() || y > layout.contentY() + layout.contentH())
                continue;
            boolean active = ThemeManager.get().isActive(theme);
            boolean hover =
                    interactive
                            && PanelStyle.contains(input.mx(), input.my(), x, y, width, cardHeight);
            ui.roundedRect(
                    x,
                    y,
                    width,
                    cardHeight,
                    5 * s,
                    hover ? PanelStyle.HOVER : PanelStyle.CARD);
            ui.outlineRoundedRect(
                    x,
                    y,
                    width,
                    cardHeight,
                    5 * s,
                    active ? .9f * s : .5f * s,
                    active ? PanelStyle.ACCENT : PanelStyle.BORDER);
            ui.text(
                    x + 8 * s,
                    y + 7 * s,
                    7 * s,
                    active ? PanelStyle.ACCENT : PanelStyle.TEXT,
                    PanelStyle.fit(theme.name(), width - 32 * s, 7 * s));
            if (active)
                ui.centeredIcon(
                        "check", x + width - 10 * s, y + 9 * s, 6 * s, PanelStyle.ACCENT);

            Map<String, Integer> colors = new java.util.LinkedHashMap<>(PanelStyle.defaultColors());
            colors.putAll(theme.colors());
            List<String> roles = PanelStyle.colorRoleOrder();
            float chipWidth = (width - 16 * s) / 11;
            for (int colorIndex = 0; colorIndex < roles.size(); colorIndex++) {
                String role = roles.get(colorIndex);
                int line = colorIndex / 11;
                int column = colorIndex % 11;
                float chipX = x + 8 * s + column * chipWidth;
                float chipY = y + (21 + line * 14) * s;
                ui.roundedRect(
                        chipX,
                        chipY,
                        Math.max(1, chipWidth - 2 * s),
                        5 * s,
                        1.5f * s,
                        colors.get(role));
                ui.text(
                        chipX,
                        chipY + 6 * s,
                        4.8f * s,
                        PanelStyle.MUTED,
                        themeRoleLabel(role));
            }
            if (hover && input.pressed()) ThemeManager.get().setActiveTheme(theme);
        }
        if (themes.isEmpty())
            ui.centeredText(
                    layout.contentX() + layout.contentW() / 2,
                    layout.contentY() + 40 * s,
                    8 * s,
                    PanelStyle.MUTED,
                    "No themes found",
                    false);
        ui.popScissor();
    }

    private static String themeRoleLabel(String role) {
        return switch (role) {
            case "background" -> "BG";
            case "surface" -> "CARD";
            case "elevatedSurface" -> "SURF";
            case "border" -> "BORD";
            case "accent" -> "ACC";
            case "text" -> "TXT";
            case "settingText" -> "SET";
            case "mutedText" -> "MUT";
            case "sectionText" -> "SECT";
            case "disabledText" -> "OFF";
            case "error" -> "ERR";
            case "enabled" -> "ON";
            case "disabled" -> "DIS";
            case "hover" -> "HOV";
            case "selected" -> "SEL";
            case "favorite" -> "FAV";
            case "sliderTrack" -> "TRK";
            case "popupBackground" -> "POP";
            case "popupSurface" -> "PSF";
            case "popupBorder" -> "PBD";
            case "white" -> "WHT";
            case "shadow" -> "SHD";
            default -> "";
        };
    }

    private void drawProfile(UiDrawList ui, float x, float y, float w, float h, float s) {
        ui.roundedRect(x, y, w, h, 4 * s, PanelStyle.CARD);
        ui.outlineRoundedRect(x, y, w, h, 4 * s, .5f * s, PanelStyle.BORDER);

        float iconSize = 15 * s;
        float iconX = x + 5 * s;
        float iconY = y + (h - iconSize) / 2;
        Identifier avatar = DiscordRpcManager.avatarTexture();
        if (avatar != null) {
            ui.image(avatar, iconX, iconY, iconSize, iconSize, 0xFFFFFFFF);
        } else {
            var player = Minecraft.getInstance().player;
            Identifier skin =
                    player != null
                            ? player.getSkin().body().texturePath()
                            : Identifier.fromNamespaceAndPath(
                                    "minecraft", "textures/entity/player/wide/steve.png");
            ui.imageRegion(
                    skin,
                    iconX,
                    iconY,
                    iconSize,
                    iconSize,
                    8 / 64f,
                    8 / 64f,
                    16 / 64f,
                    16 / 64f,
                    0xFFFFFFFF);
            ui.imageRegion(
                    skin,
                    iconX,
                    iconY,
                    iconSize,
                    iconSize,
                    40 / 64f,
                    8 / 64f,
                    48 / 64f,
                    16 / 64f,
                    0xFFFFFFFF);
        }

        String name = DiscordRpcManager.username();
        if (name == null || name.isBlank()) name = Minecraft.getInstance().getUser().getName();
        ui.pushScissor(x + 24 * s, y, w - 28 * s, h);
        ui.textCenteredVertically(
                x + 24 * s,
                y + h / 2,
                6.9f * s,
                PanelStyle.TEXT,
                PanelStyle.fit(name, w - 29 * s, 6.9f * s));
        ui.popScissor();
    }

    private boolean navigation(
            UiDrawList ui,
            PanelCards.Input input,
            boolean interactive,
            String name,
            float x,
            float y,
            float w,
            boolean selected,
            float dt) {
        float s = layout.s();
        boolean hover = PanelStyle.contains(input.mx(), input.my(), x, y, w, 23 * s);
        AnimatedFloat motion = navMotion.computeIfAbsent(name, ignored -> new AnimatedFloat(0));
        motion.setTarget(selected ? 1 : hover ? .45f : 0);
        float p = motion.update(dt, 36);
        if (p > .001f) {
            ui.roundedRect(x, y, w, 23 * s, 4 * s, PanelStyle.alpha(PanelStyle.HOVER, p));
            ui.outlineRoundedRect(x, y, w, 23 * s, 4 * s, .5f * s, PanelStyle.alpha(PanelStyle.BORDER, p));
        }
        PanelStyle.icon(
                ui,
                name,
                x + 12 * s,
                y + 11.5f * s,
                9 * s,
                selected ? PanelStyle.ACCENT : PanelStyle.MUTED);
        ui.textCenteredVertically(
                x + 23 * s,
                y + 11.5f * s,
                7.2f * s,
                selected ? PanelStyle.TEXT : PanelStyle.SELECTED,
                name);
        if (selected) {
            ui.centeredIcon(
                    "chevron-right",
                    x + w - 12.5f * s,
                    y + 11.5f * s,
                    5 * s,
                    PanelStyle.MUTED);
        }
        return interactive && hover && input.pressed();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (closing
                || settings.selectScroll(vertical)
                || settings.popupOpen()
                || settings.isDragging()) return true;
        if (layout != null && layout.containsContent((float) mx, (float) my)) {
            if (view == View.THEME)
                themeScroll =
                        Math.max(
                                0,
                                Math.min(
                                        themeMaxScroll,
                                        themeScroll - (float) vertical * 30 * layout.s()));
            else cards.scrollBy(-(float) vertical * 30 * layout.s());
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing || cards.key(event.key())) return true;
        if (searchFocused) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty())
                    search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        if (settings.selectKey(event.key())) return true;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && settings.popupOpen()) {
            settings.closePopups();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing) return true;
        if (searchFocused && event.isAllowedChatCharacter() && search.length() < 64) {
            search += event.codepointAsString();
            return true;
        }
        if (settings.selectCharacter(event.codepointAsString())) return true;
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        closing = true;
        pendingLeftClick = false;
        pendingRightClick = false;
        entrance.setTarget(0);
        cards.resetInput();
    }

    public void toggleFromKey() {
        closing = !closing;
        entrance.setTarget(closing ? 0 : 1);
        cards.resetInput();
    }

    @Override
    public void removed() {
        cards.resetInput();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean button(int button) {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button)
                == GLFW.GLFW_PRESS;
    }
}
