package dev.dlc.client.hud;

import dev.dlc.client.module.Module;
import dev.dlc.client.module.ModuleManager;
import dev.dlc.client.render.ui.UiDrawList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class HudOverlay {
    private static final int EDIT_COLOR = 0xFF72D7FF;
    private HudElement dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean previousLeftDown;

    public void render(
            Minecraft minecraft, ModuleManager modules, GuiGraphics graphics) {
        Screen screen = minecraft.screen;
        if (screen != null && !(screen instanceof ChatScreen)) {
            if (dragging != null) HudLayoutStore.saveModules(modules.all());
            dragging = null;
            previousLeftDown = false;
            return;
        }

        long windowHandle = minecraft.getWindow().handle();
        boolean leftDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
        boolean chatOpen = screen instanceof ChatScreen;
        List<HudElement> visible = visibleElements(modules);
        if (dragging != null && !visible.contains(dragging)) dragging = null;

        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        GLFW.glfwGetCursorPos(windowHandle, cursorX, cursorY);
        float guiScaleX =
                (float) minecraft.getWindow().getGuiScaledWidth()
                        / Math.max(1, minecraft.getWindow().getScreenWidth());
        float guiScaleY =
                (float) minecraft.getWindow().getGuiScaledHeight()
                        / Math.max(1, minecraft.getWindow().getScreenHeight());
        float mouseX = (float) cursorX[0] * guiScaleX;
        float mouseY = (float) cursorY[0] * guiScaleY;

        if (previousLeftDown && !leftDown && dragging != null) {
            HudLayoutStore.saveModules(modules.all());
        }
        if (!chatOpen) {
            if (dragging != null) HudLayoutStore.saveModules(modules.all());
            dragging = null;
        } else {
            if (!leftDown) {
                dragging = null;
            } else if (!previousLeftDown && dragging == null) {
                for (int i = visible.size() - 1; i >= 0; i--) {
                    HudElement element = visible.get(i);
                    if (element.draggable() && element.contains(mouseX, mouseY)) {
                        dragging = element;
                        dragOffsetX = mouseX - element.x();
                        dragOffsetY = mouseY - element.y();
                        break;
                    }
                }
            }
            if (dragging != null && leftDown && dragging.draggable()) {
                float groupOffsetX = dragging.x() - dragging.groupX();
                float groupOffsetY = dragging.y() - dragging.groupY();
                float minX = -groupOffsetX;
                float minY = -groupOffsetY;
                float maxX =
                        minecraft.getWindow().getGuiScaledWidth()
                                - dragging.groupWidth()
                                - groupOffsetX;
                float maxY =
                        minecraft.getWindow().getGuiScaledHeight()
                                - dragging.groupHeight()
                                - groupOffsetY;
                dragging.setPosition(
                        Math.max(
                                minX,
                                Math.min(maxX, mouseX - dragOffsetX)),
                        Math.max(
                                minY,
                                Math.min(maxY, mouseY - dragOffsetY)));
            }
        }
        previousLeftDown = leftDown;

        UiDrawList ui = new UiDrawList(graphics);
        for (Module module : modules.all()) {
            if (!module.enabled()) continue;
            module.renderHud(ui);
            if (!module.enabled()) continue;
            for (HudElement element : module.hudElements()) {
                if (!chatOpen || element.attached() || !element.draggable()) continue;
                boolean active = element == dragging;
                ui.outlineRoundedRect(
                        element.groupX() - 2,
                        element.groupY() - 2,
                        element.groupWidth() + 4,
                        element.groupHeight() + 4,
                        2,
                        active ? 1.5f : 1,
                        EDIT_COLOR);
            }
        }
        if (chatOpen) {
            String hint = "HUD edit: drag outlined objects";
            ui.roundedRect(4, 4, hint.length() * 4.2f + 10, 15, 3, 0xB010171D);
            ui.text(9, 7, 7, EDIT_COLOR, hint);
        }
    }

    private static List<HudElement> visibleElements(ModuleManager modules) {
        List<HudElement> elements = new ArrayList<>();
        for (Module module : modules.all()) {
            if (module.enabled()) {
                module.hudElements()
                        .stream()
                        .filter(element -> !element.attached())
                        .forEach(elements::add);
            }
        }
        return elements;
    }
}
