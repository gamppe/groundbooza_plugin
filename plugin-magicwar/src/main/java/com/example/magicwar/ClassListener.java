package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Guide and skill right-clicks, clicks inside both class screens, and the "you have to
 * pick one" rule. */
public class ClassListener implements Listener {

    private final MagicWarPlugin plugin;
    private final ArenaManager arena;
    private final ClassManager classes;
    private final ClassGuideItem guide;
    private final SkillItem skillItems;
    private final ClassController controller;
    private final SkillEffects effects;

    public ClassListener(MagicWarPlugin plugin, ArenaManager arena, ClassManager classes,
                         ClassGuideItem guide, SkillItem skillItems, ClassController controller,
                         SkillEffects effects) {
        this.plugin = plugin;
        this.arena = arena;
        this.classes = classes;
        this.guide = guide;
        this.skillItems = skillItems;
        this.controller = controller;
        this.effects = effects;
    }

    /** Deliberately NOT ignoreCancelled: a right-click on thin air can reach listeners already
     * cancelled, which is exactly what made an earlier version only respond when the player
     * happened to be aiming at a block. */
    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (guide.isGuide(event.getItem())) {
            event.setCancelled(true);
            controller.open(player);
            return;
        }
        int index = skillItems.indexOf(event.getItem());
        if (index >= 0) {
            event.setCancelled(true);
            cast(player, event.getItem(), index);
        }
    }

    /** Resolves which skill the item casts, gates it, then hands the effect to SkillEffects.
     * The cooldown uses the vanilla item cooldown, so the sweep animation comes for free, and
     * it is only started once the cast actually went through. */
    private void cast(Player player, ItemStack item, int index) {
        MagicClass magicClass = classes.classOf(player.getUniqueId());
        if (magicClass == null) {
            return;
        }
        ClassSkill skill = index < magicClass.skills().size()
                ? magicClass.skill(index)
                : advancedSkill(player);
        if (skill == null) {
            return;
        }
        if (!arena.isRunning() || arena.isLobby(player.getWorld())) {
            player.sendActionBar(Component.text("아레나에서만 사용할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        if (player.hasCooldown(item)) {
            player.sendActionBar(Component.text("쿨타임 " + (player.getCooldown(item) / 20 + 1) + "초",
                    NamedTextColor.RED));
            return;
        }
        if (!effects.cast(player, skill)) {
            return;
        }
        player.setCooldown(item, skill.cooldownSeconds() * 20);
    }

    private ClassSkill advancedSkill(Player player) {
        MagicClass.Advancement advancement = classes.advancementOf(player.getUniqueId());
        return advancement == null ? null : advancement.skill();
    }

    /** Closing the picker without choosing puts it straight back up - but only in the arena, so
     * a player sent home (or a round that ended) is never trapped in it. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClassSelectHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || classes.hasClass(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && arena.isRunning() && !arena.isLobby(player.getWorld())
                    && !classes.hasClass(player.getUniqueId())) {
                controller.openSelect(player);
            }
        });
    }
}
