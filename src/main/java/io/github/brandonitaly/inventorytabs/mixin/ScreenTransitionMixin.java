package io.github.brandonitaly.inventorytabs.mixin;

import io.github.brandonitaly.inventorytabs.TabManager;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
//?}
public class ScreenTransitionMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void keepOutgoingTabVisible(Screen next, CallbackInfo ci) {
        if (next == null && TabManager.keepTransitionScreen()) ci.cancel();
    }
}
