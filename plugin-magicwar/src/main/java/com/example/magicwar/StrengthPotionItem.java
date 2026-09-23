package com.example.magicwar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 마르지않는 힘물약, the 수도사 reward: a bottle that is never actually drunk. Right-clicking it
 * gives 힘 I for thirty seconds and starts a three minute cooldown; the bottle itself stays in
 * the inventory, so the reward is a standing ability rather than a consumable to hoard.
 *
 * <p>The cooldown is a use_cooldown group like a skill item's, which means the sweep is drawn
 * by the client and survives the bottle being moved around the inventory.
 */
public class StrengthPotionItem implements Listener {

    private static final int BUFF_TICKS = 30 * 20;
    private static final int COOLDOWN_SECONDS = 3 * 60;
    private static final String GROUP = "strength_potion";

    private final NamespacedKey tagKey;
    private final Key cooldownKey = Key.key(SkillItem.NAMESPACE, GROUP);

    public StrengthPotionItem(MagicWarPlugin plugin) {
        this.tagKey = new NamespacedKey(plugin, GROUP);
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("마르지않는 힘물약", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하여 30초간 힘 I", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("쿨타임 3분 · 병은 사라지지 않습니다", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        UseCooldownComponent cooldown = meta.getUseCooldown();
        cooldown.setCooldownSeconds(COOLDOWN_SECONDS);
        cooldown.setCooldownGroup(new NamespacedKey(SkillItem.NAMESPACE, GROUP));
        meta.setUseCooldown(cooldown);
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPotion(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE);
    }

    /** No ignoreCancelled: a right-click on thin air arrives already cancelled, and drinking in
     * mid-air is exactly how this will be used. */
    @EventHandler
    public void onDrink(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (!isPotion(event.getItem())) {
            return;
        }
        // Cancel first: otherwise vanilla starts the drinking animation and eats the bottle.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getCooldown(cooldownKey) > 0) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, BUFF_TICKS, 0));
        player.setCooldown(cooldownKey, COOLDOWN_SECONDS * 20);
        player.playSound(player, Sound.ENTITY_GENERIC_DRINK, 1f, 1.1f);
        player.sendActionBar(Component.text("마르지않는 힘물약", NamedTextColor.RED));
    }
}
