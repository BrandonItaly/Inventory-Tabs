package io.github.brandonitaly.inventorytabs;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} elif neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
*///?}

//? if fabric {
public class InventoryTabs implements ClientModInitializer {
    public static KeyMapping NEXT_TAB;
    public static KeyMapping PREVIOUS_TAB;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("inventorytabs", "controls"));

        NEXT_TAB = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.inventory_tabs.next_tab",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_TAB,
                cat
        ));

        PREVIOUS_TAB = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.inventory_tabs.previous_tab",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                cat
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> TabManager.clientTick());
    }
}
//?} elif neoforge {
/*@Mod("inventorytabs")
public class InventoryTabs {
    public static KeyMapping NEXT_TAB;
    public static KeyMapping PREVIOUS_TAB;

    public InventoryTabs(IEventBus modEventBus) {
        // Manual Client Registration to avoid Annotation issues
        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            ClientSetup.init(modEventBus);
        }
    }

    public static class ClientSetup {
        public static void init(IEventBus modEventBus) {
            modEventBus.addListener(ClientSetup::registerKeyBindings);
            NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        }

        public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
            KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("inventorytabs", "controls"));

            NEXT_TAB = new KeyMapping(
                    "key.inventory_tabs.next_tab",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_TAB,
                    cat
            );

            event.register(NEXT_TAB);
            PREVIOUS_TAB = new KeyMapping(
                    "key.inventory_tabs.previous_tab",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    cat
            );
            event.register(PREVIOUS_TAB);
        }

        public static void onClientTick(ClientTickEvent.Post event) {
            TabManager.clientTick();
        }
    }
}
*///?}
