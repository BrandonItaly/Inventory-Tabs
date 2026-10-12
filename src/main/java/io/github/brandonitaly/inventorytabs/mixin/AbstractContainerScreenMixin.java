package io.github.brandonitaly.inventorytabs.mixin;

import io.github.brandonitaly.inventorytabs.InventoryTabs;
import io.github.brandonitaly.inventorytabs.CarriedItemTransfer;
import com.mojang.blaze3d.platform.InputConstants;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}
import io.github.brandonitaly.inventorytabs.TabManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
    @Shadow private boolean isQuickCrafting;
    @Shadow private boolean doubleclick;
    @Shadow private boolean skipNextRelease;
    @Shadow @Final private java.util.Set<net.minecraft.world.inventory.Slot> quickCraftSlots;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    protected void onInit(CallbackInfo ci) {
        TabManager.onInit();
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    protected void onCloseTabs(CallbackInfo ci) {
        TabManager.cancelOpening();
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    protected void onContainerTick(CallbackInfo ci) {
        TabManager.checkAndScan();
    }

    //? if >=26.0 {
    @Inject(method = "extractCarriedItem", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "renderCarriedItem", at = @At("HEAD"))
    *///?}
    private void renderTabsBeforeCarriedItem(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        TabManager.renderTabs(context, (AbstractContainerScreen<?>)(Object)this, mouseX, mouseY);
    }

    // Redirect rendering only; clicks and server packets always see the real
    // stash slot and cursor. Vanilla still draws the stack in its foreground.
    //? if >=26.0 {
    @Redirect(method = "extractCarriedItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getCarried()Lnet/minecraft/world/item/ItemStack;"))
    //?} else {
    /*@Redirect(method = "renderCarriedItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getCarried()Lnet/minecraft/world/item/ItemStack;"))
    *///?}
    private ItemStack drawPendingCarriedItem(AbstractContainerMenu menu) {
        return CarriedItemTransfer.displayCarried(menu);
    }

    //? if >=26.0 {
    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    *///?}
    private void hideTemporaryStash(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (CarriedItemTransfer.hideStashSlot(slot)) ci.cancel();
    }

    private void inventorytabs$resetGesture() {
        isQuickCrafting = false;
        doubleclick = false;
        skipNextRelease = false;
        quickCraftSlots.clear();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    protected void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (TabManager.mouseClicked(event, doubleClick, (AbstractContainerScreen)(Object)this)) {
            inventorytabs$resetGesture();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onTabMouseDragged(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (TabManager.mouseDragged(event)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onTabMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (TabManager.mouseReleased(event)) {
            inventorytabs$resetGesture();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    protected void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Escape can always close the screen; other inventory actions wait for
        // the destination's initial inventory update.
        if ((CarriedItemTransfer.busy() || TabManager.isOpeningTab()) && event.key() != InputConstants.KEY_ESCAPE) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryTabs.NEXT_TAB != null && InventoryTabs.NEXT_TAB.matches(event) && TabManager.onNextTab()) {
            cir.setReturnValue(true);
        } else if (InventoryTabs.PREVIOUS_TAB != null && InventoryTabs.PREVIOUS_TAB.matches(event) && TabManager.onPreviousTab()) {
            cir.setReturnValue(true);
        }
    }
}
