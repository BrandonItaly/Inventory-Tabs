package io.github.brandonitaly.inventorytabs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record Tab(ItemStack icon, Component title, boolean isSelected, BlockPos pos, Runnable openAction, String key) {}
