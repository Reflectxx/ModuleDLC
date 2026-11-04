package dev.dlc.client.mixin;

import dev.dlc.client.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void moduledlc$discardStaleRenderCommands(
            DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback) {
        Render2D.discardPending();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                            shift = At.Shift.AFTER))
    private void moduledlc$renderOverlayAfterGui(
            DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback) {
        Render2D.flush();
    }
}
