package io.github.brandonitaly.inventorytabs.mixin;

import io.github.brandonitaly.inventorytabs.TabManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void preserveTabCursor(CallbackInfo ci) {
        if (TabManager.preserveCursor()) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void keepUncapturedTabCursor(CallbackInfo ci) {
        if (TabManager.preserveCursor() && !((MouseHandler)(Object)this).isMouseGrabbed()) ci.cancel();
    }

    //? if >=26.3 {
    /*@Inject(method = {"setIgnoreFirstMove", "resyncMousePosition"}, at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "setIgnoreFirstMove", at = @At("HEAD"), cancellable = true)
    //?}
    private void keepLiveTabMovement(CallbackInfo ci) {
        if (TabManager.preserveCursor() && !((MouseHandler)(Object)this).isMouseGrabbed()) ci.cancel();
    }
}
