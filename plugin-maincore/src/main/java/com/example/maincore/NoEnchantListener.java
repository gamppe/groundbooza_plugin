package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * No enchanting of any kind: the table and anvil refuse, villagers never offer enchanted
 * goods or books, and every loot source (chests, mob drops, fishing, bartering) is scrubbed -
 * enchanted books vanish, enchanted gear is kept but stripped. Job tools that carry their
 * enchantments by design (the tiered rods, the speed boots) are exempt via `exempt`.
 * ServerBridge carries a copy for farm-server.
 */
public class NoEnchantListener implements Listener {

    private final Predicate<ItemStack> exempt;

    public NoEnchantListener(Predicate<ItemStack> exempt) {
        this.exempt = exempt;
    }

    // ---------- making enchanted things ----------

    @EventHandler(ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        event.setCancelled(true);
        event.getEnchanter().sendMessage(Component.text("이 서버에서는 인챈트할 수 없습니다.", NamedTextColor.RED));
    }

    /** Anvils may still repair/rename, but never produce an enchanted result. */
    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || exempt.test(result)) {
            return;
        }
        if (isEnchanted(result) || isEnchanted(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVillagerTrade(VillagerAcquireTradeEvent event) {
        MerchantRecipe recipe = event.getRecipe();
        if (isEnchanted(recipe.getResult())) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (isEnchanted(ingredient)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ---------- looting enchanted things ----------

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        scrub(event.getLoot());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDrops(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // a player's own inventory is theirs
        }
        scrub(event.getDrops());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBarter(PiglinBarterEvent event) {
        scrub(event.getOutcome());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        ItemStack cleaned = clean(stack);
        if (cleaned == null) {
            item.remove(); // an enchanted book: nothing to hand over
        } else if (cleaned != stack) {
            item.setItemStack(cleaned);
        }
    }

    // ---------- helpers ----------

    private boolean isEnchanted(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType() == Material.ENCHANTED_BOOK) {
            return true;
        }
        if (item.getItemMeta() instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants()) {
            return true;
        }
        return !item.getEnchantments().isEmpty();
    }

    /** Removes books, strips gear. Null = drop the item entirely; same instance = untouched. */
    private ItemStack clean(ItemStack item) {
        if (item == null || exempt.test(item) || !isEnchanted(item)) {
            return item;
        }
        if (item.getType() == Material.ENCHANTED_BOOK) {
            return null;
        }
        ItemStack stripped = item.clone();
        for (Enchantment enchantment : new ArrayList<>(stripped.getEnchantments().keySet())) {
            stripped.removeEnchantment(enchantment);
        }
        return stripped;
    }

    private void scrub(List<ItemStack> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemStack cleaned = clean(items.get(i));
            if (cleaned == null) {
                items.remove(i);
            } else {
                items.set(i, cleaned);
            }
        }
    }
}
