package com.example.magicwar;

import net.kyori.adventure.key.Key;
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
        // Scroll in the off hand, target in the main hand: that is the binding gesture, and it
        // has to be checked before casting or the main-hand item would just fire its own skill.
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack mainHand = event.getItem();
        if (skillItems.isSkillItem(offHand) && mainHand != null && !skillItems.isSkillItem(mainHand)) {
            event.setCancelled(true);
            controller.bindSkill(player, offHand, mainHand);
            return;
        }
        String skillId = skillItems.skillIdOf(mainHand);
        if (skillId != null) {
            event.setCancelled(true);
            cast(player, skillId);
        }
    }

    /** Resolves which skill the item casts, gates it, then hands the effect to SkillEffects.
     * The cooldown uses the vanilla item cooldown, so the sweep animation comes for free, and
     * it is only started once the cast actually went through. */
    private void cast(Player player, String skillId) {
        ClassSkill skill = classes.skillById(player.getUniqueId(), skillId);
        if (skill == null) {
            player.sendActionBar(Component.text("이 스킬을 배우지 않았습니다.", NamedTextColor.RED));
            return;
        }
        if (!arena.isRunning() || arena.isLobby(player.getWorld())) {
            player.sendActionBar(Component.text("아레나에서만 사용할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        // Cooldown lives on the group, not the item, so every copy of a skill shares it.
        Key cooldown = SkillItem.cooldownKey(skill);
        if (player.getCooldown(cooldown) > 0) {
            player.sendActionBar(Component.text("쿨타임 " + (player.getCooldown(cooldown) / 20 + 1) + "초",
                    NamedTextColor.RED));
            return;
        }
        if (!effects.cast(player, skill)) {
            return;
        }
        player.setCooldown(cooldown, skill.cooldownSeconds() * 20);
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
        ClassSelectHolder holder = (ClassSelectHolder) event.getView().getTopInventory().getHolder();
        MagicClass base = classes.classOf(player.getUniqueId());
        int total = holder.isAdvancing()
                ? (base == null ? 0 : base.advancements().size())
                : MagicClass.values().length;
        int index = ClassSelectHolder.indexForSlot(event.getSlot(), total);
        if (index < 0) {
            return;
        }
        if (holder.isAdvancing()) {
            controller.advance(player, index);
        } else {
            controller.choose(player, index);
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
        if (event.getSlot() == ClassUpgradeHolder.SLOT_SKILLS) {
            controller.openSkills(player);
            return;
        }
        if (event.getSlot() == ClassUpgradeHolder.SLOT_ADVANCE) {
            if (classes.hasAdvanced(player.getUniqueId())) {
                return;
            }
            if (!controller.advancementUnlocked(player.getUniqueId())) {
                player.sendActionBar(Component.text("전직 퀘스트를 먼저 완료하세요.", NamedTextColor.RED));
                return;
            }
            controller.openAdvance(player);
            return;
        }
        int quest = ClassUpgradeHolder.questForSlot(event.getSlot());
        if (quest >= 0) {
            controller.claim(player, quest);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSkillListClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SkillListHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (event.getSlot() == SkillListHolder.SLOT_BACK) {
            controller.openBoard(player);
            return;
        }
        int index = SkillListHolder.indexForSlot(event.getSlot(),
                classes.ownedSkills(player.getUniqueId()).size());
        if (index < 0) {
            return;
        }
        controller.giveSkillCopy(player, index);
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
