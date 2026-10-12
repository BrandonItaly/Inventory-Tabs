package io.github.brandonitaly.inventorytabs.mixin;

import io.github.brandonitaly.inventorytabs.TabManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class RidingInventoryMixin {
    @Inject(method = "sendOpenInventory", at = @At("HEAD"))
    private void selectRiddenInventory(CallbackInfo ci) {
        TabManager.onRiddenInventoryRequested();
    }
}
