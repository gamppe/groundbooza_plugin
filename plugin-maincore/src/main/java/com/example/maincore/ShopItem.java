package com.example.maincore;

import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

/**
 * One entry in a shop category's static catalog. {@code icon} is a display-only preview shown
 * while browsing; {@code grant} produces the actual item handed to the buyer (built fresh each
 * purchase, since rental tools need a fresh expiry timestamp).
 */
public record ShopItem(String name, ItemStack icon, long price, Supplier<ItemStack> grant) {
}
