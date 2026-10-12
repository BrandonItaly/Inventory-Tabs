package io.github.brandonitaly.inventorytabs;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

public final class TabInteraction {
    private TabInteraction() {}

    public static void withShift(LocalPlayer player, boolean shift, Runnable action) {
        Input previous = player.input.keyPresses;
        Input temporary = new Input(previous.forward(), previous.backward(), previous.left(), previous.right(),
                previous.jump(), shift, previous.sprint());
        try {
            player.input.keyPresses = temporary;
            player.setShiftKeyDown(shift);
            player.connection.send(new ServerboundPlayerInputPacket(temporary));
            action.run();
        } finally {
            player.input.keyPresses = previous;
            player.setShiftKeyDown(previous.shift());
            player.connection.send(new ServerboundPlayerInputPacket(previous));
        }
    }
}
