package io.github.brandonitaly.inventorytabs;

import io.github.brandonitaly.inventorytabs.mixin.AbstractContainerScreenAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public class TabManager {
    private static final List<Tab> ACTIVE_TABS = new ArrayList<>();
    private static final TabInputState TAB_INPUT = new TabInputState();
    private static final StableTabOrder TAB_ORDER = new StableTabOrder();
    private static final long SCAN_INTERVAL_NS = 500_000_000L;
    private static final long OPEN_TIMEOUT_NS = 2_000_000_000L;
    private static long lastScanTime;
    private static Screen lastScreen;
    private static Object lastLevel;
    private static int currentPage;
    private static boolean snapToSelected;
    private static SpriteIconButton leftPageButton;
    private static SpriteIconButton rightPageButton;
    private static ItemStack playerIcon = ItemStack.EMPTY;

    private static final Identifier UNSELECTED_TAB = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2");
    private static final Identifier SELECTED_TAB = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_selected_2");

    private static OpeningRequest openingRequest;
    private static BlockPos openedBlockPos;
    private static Entity openedEntity;

    private static final class OpeningRequest {
        final BlockPos pos;
        final Entity entity;
        final long deadline = System.nanoTime() + OPEN_TIMEOUT_NS;
        boolean completed;

        OpeningRequest(BlockPos pos, Entity entity) {
            this.pos = pos;
            this.entity = entity;
        }
    }

    public static void onInit() {
        Minecraft client = Minecraft.getInstance();
        boolean newScreen = client.screen != lastScreen;
        if (isOpeningTab() && newScreen) {
            openedBlockPos = openingRequest.pos;
            openedEntity = openingRequest.entity;
            // Keep cursor preservation until the next tick, after screen initialization.
            openingRequest.completed = true;
            CarriedItemTransfer.onScreenOpened();
        } else if (newScreen) {
            TAB_ORDER.clear();
            openedBlockPos = null;
            openedEntity = null;
            currentPage = 0;
            if (!(client.screen instanceof InventoryScreen) && client.hitResult != null) {
                if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                    openedBlockPos = hit.getBlockPos();
                } else if (client.hitResult instanceof EntityHitResult hit) {
                    openedEntity = hit.getEntity();
                }
            }
        }
        snapToSelected = newScreen;
        lastScanTime = 0;
        leftPageButton = SpriteIconButton.builder(Component.translatable("inventorytabs.previous_page"), b -> {
            if (currentPage > 0) currentPage--;
        }, true).size(12, 18).sprite(Identifier.fromNamespaceAndPath("inventorytabs", "arrow_left"), 8, 8).build();
        rightPageButton = SpriteIconButton.builder(Component.translatable("inventorytabs.next_page"), b -> {
            currentPage++;
        }, true).size(12, 18).sprite(Identifier.fromNamespaceAndPath("inventorytabs", "arrow_right"), 8, 8).build();
    }

    private static void finishOpening() {
        openingRequest = null;
    }

    public static void cancelOpening() {
        finishOpening();
        CarriedItemTransfer.cancel();
        TAB_INPUT.clear();
    }

    public static boolean preserveCursor() {
        Minecraft client = Minecraft.getInstance();
        return openingRequest != null && System.nanoTime() < openingRequest.deadline && client.player != null && client.level != null
                && !client.player.isDeadOrDying()
                && client.isWindowActive()
                && (client.screen == null || client.screen instanceof AbstractContainerScreen);
    }

    public static boolean keepTransitionScreen() {
        Minecraft client = Minecraft.getInstance();
        return isOpeningTab() && preserveCursor() && client.screen instanceof AbstractContainerScreen;
    }

    public static boolean isOpeningTab() { return openingRequest != null && !openingRequest.completed; }

    // Called on both loaders, including outside container screens and after disconnecting.
    public static void clientTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != lastLevel || client.player == null) {
            lastLevel = client.level;
            openedBlockPos = null;
            openedEntity = null;
            finishOpening();
            CarriedItemTransfer.cancel();
            TAB_INPUT.clear();
            playerIcon = ItemStack.EMPTY;
            lastScanTime = 0;
            TAB_ORDER.clear();
        }
        if (isOpeningTab() && System.nanoTime() >= openingRequest.deadline) {
            finishOpening();
            CarriedItemTransfer.openFailed();
            // Open inventory if a failed tab switch leaves the old container screen visible.
            if (client.player != null && client.screen instanceof AbstractContainerScreen<?> screen
                    && screen.getMenu() != client.player.containerMenu) client.setScreen(new InventoryScreen(client.player));
            if (client.screen == null && client.player != null) client.mouseHandler.grabMouse();
        }
        if (openingRequest != null && openingRequest.completed) {
            finishOpening();
        }
        CarriedItemTransfer.tick();
        if (!(client.screen instanceof AbstractContainerScreen) || client.screen instanceof CreativeModeInventoryScreen) {
            ACTIVE_TABS.clear();
            lastScreen = null;
            if (!isOpeningTab()) {
                TAB_ORDER.clear();
                openedBlockPos = null;
                openedEntity = null;
            }
        } else {
            checkAndScan();
        }
    }

    public static void checkAndScan() {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (client.screen != lastScreen || now - lastScanTime >= SCAN_INTERVAL_NS) {
            lastScreen = client.screen;
            lastScanTime = now;
            scan();
        }
    }

    private static void scan() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ACTIVE_TABS.clear();
        if (player == null || client.level == null || !(client.screen instanceof AbstractContainerScreen)
                || client.screen instanceof CreativeModeInventoryScreen) return;

        if (playerIcon.isEmpty()) {
            playerIcon = new ItemStack(Items.PLAYER_HEAD);
            playerIcon.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        }
        boolean playerSelected = client.screen instanceof InventoryScreen;
        ACTIVE_TABS.add(new Tab(playerIcon, Component.translatable("container.inventory"), playerSelected, null, () -> {
            if (!beginOpening(null, null)) return;
            player.closeContainer();
            client.setScreen(new InventoryScreen(player));
        }, "player"));

        double reach = player.blockInteractionRange();
        int radius = (int) Math.ceil(reach);
        BlockPos center = player.blockPosition();
        Set<BlockPos> seenChests = new HashSet<>();
        List<Tab> nearby = new ArrayList<>();
        for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (!player.isWithinBlockInteractionRange(mutable, 0) || !client.level.hasChunkAt(mutable)) continue;
            BlockState state = client.level.getBlockState(mutable);
            MenuProvider provider = state.getMenuProvider(client.level, mutable);
            boolean enderChest = state.is(Blocks.ENDER_CHEST);
            if (provider == null && !enderChest) continue;
            BlockPos pos = mutable.immutable();
            boolean selected = !playerSelected && pos.equals(openedBlockPos);
            boolean renamed = client.level.getBlockEntity(pos) instanceof Nameable nameable && nameable.hasCustomName();
            renamed |= provider instanceof Nameable nameable && nameable.hasCustomName();
            BlockPos identity = pos;
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
                BlockState partnerState = client.level.getBlockState(partner);
                if (partnerState.getBlock() == state.getBlock()
                        && partnerState.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite()
                        && partnerState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)) {
                    selected |= !playerSelected && partner.equals(openedBlockPos);
                    renamed |= client.level.getBlockEntity(partner) instanceof Nameable nameable && nameable.hasCustomName();
                    if (seenChests.contains(pos)) continue;
                    seenChests.add(partner);
                    if (partner.asLong() < pos.asLong()) identity = partner;
                }
            }
            Component title = renamed && provider != null ? provider.getDisplayName() : state.getBlock().getName();
            nearby.add(new Tab(new ItemStack(state.getBlock()), title, selected, pos, () -> openBlock(pos),
                    "block:" + identity.asLong()));
        }

        double entityReach = player.entityInteractionRange();
        List<Entity> entities = client.level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(entityReach),
                entity -> entity != player && entity.isAlive() && player.isWithinEntityInteractionRange(entity, 0)
                        && canOpenEntityInventory(entity, player));
        entities.sort(Comparator.comparingInt(Entity::getId));
        for (Entity entity : entities) {
            ItemStack icon = entityIcon(entity);
            nearby.add(new Tab(icon, entity.getDisplayName(), !playerSelected && entity == openedEntity,
                    entity.blockPosition(), () -> openEntity(entity), "entity:" + entity.getUUID()));
        }
        nearby.sort(Comparator.comparingDouble((Tab tab) -> player.distanceToSqr(Vec3.atCenterOf(tab.pos())))
                .thenComparingLong(tab -> tab.pos().asLong()));
        List<String> order = TAB_ORDER.update(nearby.stream().map(Tab::key).toList());
        Map<String, Tab> tabsByKey = new java.util.HashMap<>();
        for (Tab tab : nearby) tabsByKey.put(tab.key(), tab);
        for (String key : order) ACTIVE_TABS.add(tabsByKey.get(key));
    }

    private static ItemStack entityIcon(Entity entity) {
        ItemStack picked = entity.getPickResult();
        if (picked != null && !picked.isEmpty()) return picked.copy();
        Object egg = SpawnEggItem.byId(entity.getType());
        if (egg instanceof java.util.Optional<?> optional) egg = optional.orElse(null);
        if (egg instanceof net.minecraft.core.Holder<?> holder) egg = holder.value();
        if (egg instanceof Item item) return new ItemStack(item);
        return new ItemStack(entity instanceof AbstractVillager ? Items.EMERALD : Items.CHEST);
    }

    private static boolean beginOpening(BlockPos pos, Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (isOpeningTab() || client.player == null || client.level == null || client.gameMode == null
                || !client.player.containerMenu.getCarried().isEmpty()) return false;
        openingRequest = new OpeningRequest(pos, entity);
        return true;
    }

    public static void onRiddenInventoryRequested() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof HasCustomInventoryScreen && canOpenEntityInventory(vehicle, player)) {
            beginOpening(null, vehicle);
        }
    }

    private static void openBlock(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null || !player.isWithinBlockInteractionRange(pos, 0)) return;
        BlockState state = client.level.getBlockState(pos);
        if (state.getMenuProvider(client.level, pos) == null && !state.is(Blocks.ENDER_CHEST)) return;
        if (!beginOpening(pos, null)) return;
        withShift(player, false, () -> {
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        });
    }

    private static void openEntity(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || !entity.isAlive() || !canOpenEntityInventory(entity, player) || !player.isWithinEntityInteractionRange(entity, 0)
                || !beginOpening(null, entity)) return;
        if (player.getVehicle() == entity && entity instanceof HasCustomInventoryScreen) {
            player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
        } else {
            withShift(player, true, () -> {
                client.gameMode.interact(player, entity, /*? if >=26.0 {*/ new EntityHitResult(entity), /*?}*/ InteractionHand.MAIN_HAND);
            });
        }
    }

    private static boolean canOpenEntityInventory(Entity entity, LocalPlayer player) {
        if (entity instanceof AbstractHorse horse) {
            return horse.isTamed() && !horse.isBaby() && (!horse.isVehicle() || player.getVehicle() == horse);
        }
        return entity instanceof Container || entity instanceof HasCustomInventoryScreen || entity instanceof AbstractVillager;
    }

    private static void withShift(LocalPlayer player, boolean shift, Runnable action) {
        try {
            TabInteraction.withShift(player, shift, action);
        } catch (RuntimeException failure) {
            finishOpening();
            throw failure;
        }
    }
    private record Layout(int x, int y, int capacity, int pages, int first, int end) {}

    private static Layout layout(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        int capacity = Math.max(1, accessor.getImageWidth() / 27);
        if (snapToSelected) {
            for (int i = 0; i < ACTIVE_TABS.size(); i++) {
                if (ACTIVE_TABS.get(i).isSelected()) {
                    currentPage = i / capacity;
                    break;
                }
            }
            snapToSelected = false;
        }
        int pages = Math.max(1, (ACTIVE_TABS.size() + capacity - 1) / capacity);
        currentPage = Math.max(0, Math.min(currentPage, pages - 1));
        int padding = (accessor.getImageWidth() - capacity * 27) / 2;
        int first = currentPage * capacity;
        Layout result = new Layout(accessor.getLeftPos() + padding, accessor.getTopPos() - 28,
                capacity, pages, first, Math.min(first + capacity, ACTIVE_TABS.size()));
        if (leftPageButton != null) {
            leftPageButton.setX(result.x() - 14);
            leftPageButton.setY(result.y() + 7);
            leftPageButton.active = currentPage > 0;
        }
        if (rightPageButton != null) {
            rightPageButton.setX(result.x() + capacity * 27 + 2);
            rightPageButton.setY(result.y() + 7);
            rightPageButton.active = currentPage < pages - 1;
        }
        return result;
    }

    public static void renderTabs(GuiGraphicsExtractor context, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        checkAndScan();
        if (ACTIVE_TABS.isEmpty()) return;
        Layout layout = layout(screen);
        for (int i = layout.first(); i < layout.end(); i++) {
            Tab tab = ACTIVE_TABS.get(i);
            int x = layout.x() + (i - layout.first()) * 27;
            int y = layout.y();
            // The bottom four pixels of an inactive creative tab belong behind
            // the container frame. Clip them instead of painting over its rim.
            if (!tab.isSelected()) context.enableScissor(x, y, x + 26, y + 28);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, tab.isSelected() ? SELECTED_TAB : UNSELECTED_TAB, x, y, 26, 32);
            if (!tab.isSelected()) context.disableScissor();
            //~ if >=26.0 'renderItem' -> 'item' {
            context.item(tab.icon(), x + 5, y + 9);//~}
            if (contains(mouseX, mouseY, x, y, 26, 28)) {
                Minecraft client = Minecraft.getInstance();
                boolean carrying = client.player != null && !client.player.containerMenu.getCarried().isEmpty();
                boolean noSpace = carrying && !CarriedItemTransfer.canTransfer();
                if (!tab.isSelected() && !noSpace && !isOpeningTab() && !CarriedItemTransfer.busy()) context.requestCursor(CursorTypes.POINTING_HAND);
                Component tooltip = !tab.isSelected() && noSpace
                        ? Component.translatable("inventorytabs.transfer_no_space") : tab.title();
                context.setTooltipForNextFrame(client.font, tooltip, mouseX, mouseY);
            }
        }
        if (layout.pages() > 1) {
            //~ if >=26.0 'render' -> 'extractRenderState' {
            leftPageButton.extractRenderState(context, mouseX, mouseY, 0.0F);//~}
            //~ if >=26.0 'render' -> 'extractRenderState' {
            rightPageButton.extractRenderState(context, mouseX, mouseY, 0.0F);//~}
        }
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, AbstractContainerScreen<?> screen) {
        TAB_INPUT.beginPress(event.button());
        if (CarriedItemTransfer.busy() || isOpeningTab()) {
            TAB_INPUT.capture(event.button());
            return true;
        }
        //? if >=26.3 {
        boolean leftClick = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        //?} else {
        /*boolean leftClick = event.button() == 0;
        *///?}
        checkAndScan();
        if (ACTIVE_TABS.isEmpty()) return false;
        Layout layout = layout(screen);
        // Swallow the entire gesture for every button rather than treating the arrow as outside inventory.
        if (layout.pages() > 1) {
            for (SpriteIconButton button : List.of(leftPageButton, rightPageButton)) {
                if (contains(event.x(), event.y(), button.getX(), button.getY(), 12, 18)) {
                    TAB_INPUT.capture(event.button());
                    if (leftClick) button.mouseClicked(event, doubleClick);
                    return true;
                }
            }
        }
        for (int i = layout.first(); i < layout.end(); i++) {
            int x = layout.x() + (i - layout.first()) * 27;
            if (contains(event.x(), event.y(), x, layout.y(), 26, 28)) {
                Tab tab = ACTIVE_TABS.get(i);
                TAB_INPUT.capture(event.button());
                if (leftClick && !tab.isSelected() && !isOpeningTab()) CarriedItemTransfer.switchTab(tab.openAction());
                return true;
            }
        }
        return false;
    }

    public static boolean mouseDragged(MouseButtonEvent event) {
        return TAB_INPUT.dragging(event.button()) || CarriedItemTransfer.busy() || isOpeningTab();
    }

    public static boolean mouseReleased(MouseButtonEvent event) {
        return TAB_INPUT.release(event.button()) || CarriedItemTransfer.busy() || isOpeningTab();
    }

    public static boolean onNextTab() {
        return cycleTab(1);
    }

    public static boolean onPreviousTab() {
        return cycleTab(-1);
    }

    private static boolean cycleTab(int direction) {
        if (CarriedItemTransfer.busy() || isOpeningTab()) return true;
        checkAndScan();
        if (ACTIVE_TABS.isEmpty()) return false;
        int selected = -1;
        for (int i = 0; i < ACTIVE_TABS.size(); i++) {
            if (ACTIVE_TABS.get(i).isSelected()) {
                selected = i;
                break;
            }
        }
        int target = selected < 0
                ? (direction > 0 ? 0 : ACTIVE_TABS.size() - 1)
                : Math.floorMod(selected + direction, ACTIVE_TABS.size());
        Tab next = ACTIVE_TABS.get(target);
        if (!next.isSelected()) CarriedItemTransfer.switchTab(next.openAction());
        return true;
    }
}
