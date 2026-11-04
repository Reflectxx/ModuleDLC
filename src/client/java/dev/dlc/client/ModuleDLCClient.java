package dev.dlc.client;

import dev.dlc.client.hud.HudOverlay;
import dev.dlc.client.hud.HudLayoutStore;
import dev.dlc.client.script.ScriptErrorReporter;
import dev.dlc.client.ui.CsKeybindManager;
import dev.dlc.client.ui.panel.Panel;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

public class ModuleDLCClient implements ClientModInitializer {
    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        ClientCore core = ClientCore.getInstance();
        CsKeybindManager keybinds = CsKeybindManager.get();
        HudOverlay hudOverlay = new HudOverlay();
        DiscordRpcManager discordRpc = new DiscordRpcManager();
        core.scripts().start();
        discordRpc.start();
        ClientTickEvents.END_CLIENT_TICK.register(client -> keybinds.tick(client, core.modules()));
        ClientTickEvents.END_CLIENT_TICK.register(core.modules()::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ScriptErrorReporter::flushNotifications);
        HudRenderCallback.EVENT.register(
                (graphics, tickCounter) -> {
                    var minecraft = net.minecraft.client.Minecraft.getInstance();
                    core.modules().frame(minecraft);
                    hudOverlay.render(minecraft, core.modules(), graphics);
                });
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> {
                    HudLayoutStore.saveModules(core.modules().all());
                    core.scripts().close();
                    discordRpc.stop();
                });

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.moduledlc.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen instanceof Panel panel) {
                    panel.toggleFromKey();
                } else {
                    client.setScreen(new Panel());
                }
            }
        });
    }
}
