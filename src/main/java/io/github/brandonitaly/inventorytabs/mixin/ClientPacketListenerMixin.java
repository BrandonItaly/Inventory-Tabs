package io.github.brandonitaly.inventorytabs.mixin;

import io.github.brandonitaly.inventorytabs.CarriedItemTransfer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void confirmTransfer(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        CarriedItemTransfer.onContents(packet);
    }
}
