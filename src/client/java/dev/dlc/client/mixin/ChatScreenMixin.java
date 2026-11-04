package dev.dlc.client.mixin;

import dev.dlc.client.script.ClientCommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void moduledlc$runClientCommand(
            String message, boolean addToHistory, CallbackInfo callback) {
        if (ClientCommandManager.get().executeIfRecognized(message)) {
            Minecraft.getInstance().setScreen(null);
            callback.cancel();
        }
    }
}
