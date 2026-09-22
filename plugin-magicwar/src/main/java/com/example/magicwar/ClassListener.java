package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
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

    public ClassListener(MagicWarPlugin plugin, ArenaManager arena, ClassManager classes,
                         ClassGuideItem guide, SkillItem skillItems, ClassController controller) {
        this.plugin = plugin;
        this.arena = arena;
        this.classes = classes;
        this.guide = guide;
        this.skillItems = skillItems;
        this.controller = controller;
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

    /** Placeholder cast: the cooldown, the feedback and the gating are real, what the skill
     * actually does is not decided yet. The cooldown uses the vanilla item cooldown, so the
     * sweep animation on the hotbar comes for free. */
    private void cast(Player player, ItemStack item, int index) {
        MagicClass magicClass = classes.classOf(player.getUniqueId());
        if (magicClass == null || index >= magicClass.skills().size()) {
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
        ClassSkill skill = magicClass.skill(index);
        player.setCooldown(item, skill.cooldownSeconds() * 20);

        Location from = player.getEyeLocation();
        player.getWorld().spawnParticle(Particle.ENCHANT, from.clone().add(from.getDirection().multiply(1.5)),
                40, 0.4, 0.4, 0.4, 0.5);
        player.getWorld().playSound(from, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        player.sendActionBar(Component.text(skill.name() + " 사용!", NamedTextColor.AQUA));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSelectClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClassSelectHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        MagicClass picked = ClassSelectHolder.classForSlot(event.getSlot());
        if (picked != null) {
            controller.choose(player, picked);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUpgradeClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClassUpgradeHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int track = ClassUpgradeHolder.trackForIconSlot(event.getSlot());
        if (track >= 0) {
            controller.upgrade(player, track);
        }
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
