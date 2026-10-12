package io.github.brandonitaly.inventorytabs.mixin;

import io.github.brandonitaly.inventorytabs.CarriedItemTransfer;
import io.github.brandonitaly.inventorytabs.TabManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class TransferGuardMixin {
    @Inject(method = {"handleContainerInput", "handlePlaceRecipe", "handleInventoryButtonClick"},
            at = @At("HEAD"), cancellable = true)
    private void guardTransfer(CallbackInfo ci) {
        if ((CarriedItemTransfer.busy() || TabManager.isOpeningTab()) && !CarriedItemTransfer.isTransferClick()) ci.cancel();
    }
}
