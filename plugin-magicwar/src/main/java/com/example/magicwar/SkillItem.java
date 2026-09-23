package com.example.magicwar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Castable items. A skill is identified by its id alone, so the same skill can live on the
 * banner pattern it ships as, on a copy pulled from the skill list, or stamped onto whatever
 * the player was holding - all three cast the same way.
 *
 * <p>Every one of them carries a use_cooldown component pointing at the same cooldown group
 * ({@code magicwar:<skill id>}), which is what makes copies share a cooldown and lets the
 * vanilla sweep animation show on all of them at once.
 */
public class SkillItem {

    public static final String NAMESPACE = "magicwar";

    private final NamespacedKey idKey;
    private final NamespacedKey slotKey;

    public SkillItem(MagicWarPlugin plugin) {
        this.idKey = new NamespacedKey(plugin, "skill_id");
        this.slotKey = new NamespacedKey(plugin, "skill_slot");
    }

    /** The cooldown group shared by every item carrying this skill. */
    public static Key cooldownKey(ClassSkill skill) {
        return Key.key(NAMESPACE, skill.id());
    }

    public ItemStack create(ClassSkill skill, int slot, String owner) {
        return stamp(new ItemStack(skill.icon()), skill, slot, owner, false);
    }

    /** Puts the skill onto an item the player already has, leaving it otherwise as it was. */
    public ItemStack bind(ItemStack target, ClassSkill skill, int slot, String owner) {
        return stamp(target, skill, slot, owner, true);
    }

    private ItemStack stamp(ItemStack item, ClassSkill skill, int slot, String owner, boolean bound) {
        ItemMeta meta = item.getItemMeta();
        if (!bound) {
            meta.displayName(Component.text("[" + (slot + 1) + "] " + skill.name(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.setEnchantmentGlintOverride(true);
        }
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.text((bound ? "부여된 스킬 " : "") + "[" + (slot + 1) + "] " + skill.name(),
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(owner + " 스킬 · 우클릭하여 사용 · 쿨타임 " + skill.cooldownSeconds() + "초",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // Same group on every copy → one shared cooldown, drawn on all of them.
        UseCooldownComponent cooldown = meta.getUseCooldown();
        cooldown.setCooldownSeconds(skill.cooldownSeconds());
        cooldown.setCooldownGroup(new NamespacedKey(NAMESPACE, skill.id()));
        meta.setUseCooldown(cooldown);

        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, skill.id());
        meta.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, slot);
        item.setItemMeta(meta);
        return item;
    }

    /** The skill id this item casts, or null when it is not a skill item. */
    public String skillIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean isSkillItem(ItemStack item) {
        return skillIdOf(item) != null;
    }

    /** Re-stamps every skill item the player carries. An advancement can rename or re-time a
     * skill it inherits, and the name and cooldown are baked into the item at creation - so
     * without this the hotbar would still read 파동탄 after becoming a 서리기사. */
    public void refreshCarried(Player player, List<ClassSkill> owned, String owner) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            String id = skillIdOf(contents[i]);
            if (id == null) {
                continue;
            }
            for (int slot = 0; slot < owned.size(); slot++) {
                ClassSkill skill = owned.get(slot);
                if (!skill.id().equals(id)) {
                    continue;
                }
                boolean bound = contents[i].getType() != skill.icon();
                ItemStack replacement = bound
                        ? stampFresh(contents[i], skill, slot, owner, true)
                        : create(skill, slot, owner);
                replacement.setAmount(contents[i].getAmount());
                player.getInventory().setItem(i, replacement);
                break;
            }
        }
    }

    /** Re-stamp that drops the lore the previous stamp added, so repeated renames do not pile
     * lines up on a bound item. */
    private ItemStack stampFresh(ItemStack item, ClassSkill skill, int slot, String owner, boolean bound) {
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            if (lore.size() >= 2) {
                lore.subList(lore.size() - 2, lore.size()).clear();
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return stamp(item, skill, slot, owner, bound);
    }
}
