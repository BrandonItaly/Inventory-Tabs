package io.github.brandonitaly.inventorytabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CarriedItemTransfer {
    private static final long TIMEOUT = 5_000_000_000L;
    private static CarriedItemTransfer active;
    private static final ThreadLocal<Boolean> transferClick = ThreadLocal.withInitial(() -> false);
    private enum Phase { OPENING, WAITING_FOR_CONTENTS, READY_TO_RESTORE }
    private final LocalPlayer player;
    private final Object level;
    private final AbstractContainerMenu origin;
    private final int inventorySlot;
    private final ItemStack stack;
    private AbstractContainerMenu menu;
    private long deadline = System.nanoTime() + TIMEOUT;
    private Phase phase = Phase.OPENING;

    private CarriedItemTransfer(LocalPlayer player, Slot slot) {
        this.player = player;
        this.level = Minecraft.getInstance().level;
        this.origin = this.menu = player.containerMenu;
        this.inventorySlot = slot.getContainerSlot();
        this.stack = origin.getCarried().copy();
    }

    public static boolean busy() { return active != null; }
    public static boolean isTransferClick() { return transferClick.get(); }

    private boolean validContext(Minecraft client) {
        return client.player == player && client.level == level && client.gameMode != null && !player.isDeadOrDying();
    }

    private boolean isCurrentMenu(Minecraft client, AbstractContainerMenu expected) {
        return client.screen instanceof AbstractContainerScreen && player.containerMenu == expected;
    }

    private void finish() {
        if (active == this) active = null;
    }

    public static ItemStack displayCarried(AbstractContainerMenu menu) {
        ItemStack actual = menu.getCarried();
        CarriedItemTransfer transfer = active;
        Minecraft client = Minecraft.getInstance();
        if (!actual.isEmpty() || transfer == null || client.player != transfer.player || client.level != transfer.level
                || (menu != transfer.origin && menu != transfer.menu)) return actual;
        return transfer.stack.copy();
    }

    public static boolean hideStashSlot(Slot slot) {
        CarriedItemTransfer transfer = active;
        Minecraft client = Minecraft.getInstance();
        return transfer != null && client.player == transfer.player && client.level == transfer.level
                && slot.container == transfer.player.getInventory() && slot.getContainerSlot() == transfer.inventorySlot
                && ItemStack.matches(slot.getItem(), transfer.stack);
    }

    private static Slot slot(AbstractContainerMenu menu, LocalPlayer player, int index) {
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == index && slot.isActive()) return slot;
        }
        return null;
    }

    private static Slot emptySlot(LocalPlayer player) {
        ItemStack stack = player.containerMenu.getCarried();
        for (Slot slot : player.containerMenu.slots) {
            int index = slot.getContainerSlot();
            if (slot.container == player.getInventory() && index >= 0 && index < 36 && slot.isActive()
                    && slot.getItem().isEmpty() && slot.mayPlace(stack) && slot.mayPickup(player)
                    && slot.getMaxStackSize(stack) >= stack.getCount()) return slot;
        }
        return null;
    }

    public static boolean canTransfer() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && emptySlot(player) != null;
    }

    public static void switchTab(Runnable open) {
        Minecraft client = Minecraft.getInstance();
        if (busy() || client.player == null || client.level == null || client.gameMode == null
                || client.player.isDeadOrDying()) return;
        if (client.player.containerMenu.getCarried().isEmpty()) {
            open.run();
            return;
        }
        Slot slot = emptySlot(client.player);
        if (slot == null) {
            message("inventorytabs.transfer_no_space");
            return;
        }
        CarriedItemTransfer transfer = new CarriedItemTransfer(client.player, slot);
        active = transfer;
        transfer.click(slot);
        if (active != transfer) return;
        if (!transfer.origin.getCarried().isEmpty() || !ItemStack.matches(slot.getItem(), transfer.stack)) {
            transfer.finish();
            return;
        }
        try {
            open.run();
        } catch (RuntimeException failure) {
            try { if (active == transfer) openFailed(); }
            catch (RuntimeException recoveryFailure) { failure.addSuppressed(recoveryFailure); }
            transfer.finish();
            throw failure;
        }
    }

    private void click(Slot slot) {
        boolean previousClick = transferClick.get();
        transferClick.set(true);
        try {
            Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, player);
        } catch (RuntimeException failure) {
            finish();
            throw failure;
        } finally {
            if (previousClick) transferClick.set(true);
            else transferClick.remove();
        }
    }

    public static void onScreenOpened() {
        CarriedItemTransfer transfer = active;
        if (transfer == null || transfer.phase != Phase.OPENING) return;
        Minecraft client = Minecraft.getInstance();
        if (!transfer.validContext(client)) {
            transfer.finish();
            return;
        }
        transfer.menu = transfer.player.containerMenu;
        if (transfer.menu == transfer.origin) return;
        if (slot(transfer.menu, transfer.player, transfer.inventorySlot) == null) {
            transfer.finish();
            return;
        }
        transfer.deadline = System.nanoTime() + TIMEOUT;
        transfer.phase = client.screen instanceof InventoryScreen ? Phase.READY_TO_RESTORE : Phase.WAITING_FOR_CONTENTS;
    }

    public static void onContents(ClientboundContainerSetContentPacket packet) {
        CarriedItemTransfer transfer = active;
        if (transfer == null) return;
        Minecraft client = Minecraft.getInstance();
        if (!transfer.validContext(client)
                || (transfer.phase != Phase.OPENING && !transfer.isCurrentMenu(client, transfer.menu))) {
            transfer.finish();
            return;
        }
        if (transfer.phase == Phase.OPENING || packet.containerId() != transfer.menu.containerId) return;
        Slot slot = slot(transfer.menu, transfer.player, transfer.inventorySlot);
        if (slot == null || slot.index < 0 || slot.index >= packet.items().size()) {
            transfer.finish();
            return;
        }
        ItemStack stored = packet.items().get(slot.index);
        if (packet.carriedItem().isEmpty() && ItemStack.matches(stored, transfer.stack)) {
            transfer.phase = Phase.READY_TO_RESTORE;
        } else {
            transfer.finish();
        }
    }

    private void restore() {
        Minecraft client = Minecraft.getInstance();
        Slot slot = slot(menu, player, inventorySlot);
        if (!validContext(client) || !isCurrentMenu(client, menu) || slot == null || !menu.getCarried().isEmpty()
                || !ItemStack.matches(slot.getItem(), stack) || !slot.mayPickup(player)) {
            finish();
            return;
        }
        click(slot);
        finish();
    }

    public static void openFailed() {
        CarriedItemTransfer transfer = active;
        if (transfer == null) return;
        Minecraft client = Minecraft.getInstance();
        if (transfer.validContext(client) && transfer.isCurrentMenu(client, transfer.origin)) {
            transfer.menu = transfer.origin;
            transfer.restore();
        } else {
            transfer.finish();
        }
    }

    public static void tick() {
        CarriedItemTransfer transfer = active;
        if (transfer == null) return;
        Minecraft client = Minecraft.getInstance();
        if (!transfer.validContext(client)
                || (client.screen != null && !(client.screen instanceof AbstractContainerScreen))
                || (transfer.phase != Phase.OPENING && !transfer.isCurrentMenu(client, transfer.menu))) {
            transfer.finish();
            return;
        }
        if (System.nanoTime() >= transfer.deadline) {
            openFailed();
            return;
        }
        if (transfer.phase == Phase.READY_TO_RESTORE) {
            transfer.restore();
        }
    }

    public static void cancel() { active = null; }

    private static void message(String key) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        //? if >=26.0 {
        player.sendOverlayMessage(Component.translatable(key));
        //?} else {
        /*player.displayClientMessage(Component.translatable(key), true);
        *///?}
    }
}
