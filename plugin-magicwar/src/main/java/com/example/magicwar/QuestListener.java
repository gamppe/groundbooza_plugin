package com.example.magicwar;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Everything on the vanilla side of the quest board: kills, blocks, what is being worn, what is
 * being eaten, and the handful of one-off gestures a quest asks for. The spells report
 * themselves from {@link SkillEffects}.
 *
 * <p>Several quests care not just that something died but how, so the kill handler looks at the
 * weapon and at the victim before deciding which goals to raise. A kill can satisfy more than
 * one goal name; {@link QuestManager} still only credits one quest per goal.
 */
public class QuestListener implements Listener {

    /** How far a 네크로맨서 notices a death. */
    private static final double NEARBY_DEATH_RADIUS = 32;

    /** Punching fire out can arrive as a break and as a left click, depending on how the
     * server routes it. Either is proof enough; two of them are still one fire. */
    private static final long EXTINGUISH_DEBOUNCE_MILLIS = 250;

    private final QuestTracker tracker;
    private final Summons summons;
    private final Map<UUID, Long> lastExtinguish = new HashMap<>();

    public QuestListener(QuestTracker tracker, Summons summons) {
        this.tracker = tracker;
        this.summons = summons;
    }

    // ---------- dying ----------

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        String type = victim.getType().name();
        Player killer = victim.getKiller();

        if (killer != null && tracker.counts(killer)) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            tracker.fire(killer, Quest.Goal.KILL, type);
            tracker.fire(killer, Quest.Goal.KILL_QUOTA, type);
            tracker.fire(killer, Quest.Goal.DISTINCT_MOB, type);
            if (isSword(weapon)) {
                tracker.fire(killer, Quest.Goal.KILL_SWORD, type);
            }
            if (weapon.getType().isAir()) {
                tracker.fire(killer, Quest.Goal.KILL_FIST, type);
                if (victim instanceof Monster) {
                    tracker.fire(killer, Quest.Goal.KILL_HOSTILE_FIST, type);
                }
            }
            // Checked before the fire goes out with the mob - by the death event it is still on.
            if (victim.getFireTicks() > 0) {
                tracker.fire(killer, Quest.Goal.KILL_BURNING, type);
            }
        }

        Player owner = summonKiller(victim);
        if (owner != null) {
            tracker.fire(owner, Quest.Goal.SUMMON_KILL, type);
        }
        creditNearbyDeaths(victim);
    }

    /** The player whose summon landed the killing blow, or null. */
    private Player summonKiller(LivingEntity victim) {
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent cause)) {
            return null;
        }
        Entity damager = cause.getDamager();
        if (!(damager instanceof Mob mob)) {
            return null;
        }
        UUID owner = summons.ownerOf(mob);
        return owner == null ? null : Bukkit.getPlayer(owner);
    }

    /** 주위에서 죽은 몹: everyone close enough gets the tick, whoever did the killing. */
    private void creditNearbyDeaths(LivingEntity victim) {
        if (victim instanceof Player) {
            return;
        }
        for (Entity nearby : victim.getWorld().getNearbyEntities(victim.getLocation(),
                NEARBY_DEATH_RADIUS, NEARBY_DEATH_RADIUS, NEARBY_DEATH_RADIUS)) {
            if (nearby instanceof Player player) {
                tracker.fire(player, Quest.Goal.NEARBY_DEATH, null);
            }
        }
    }

    // ---------- blocks ----------

    @EventHandler(ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            extinguished(event.getPlayer());
            return;
        }
        tracker.fire(event.getPlayer(), Quest.Goal.MINE, type.name());
    }

    // ---------- carrying and eating ----------

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack stack = event.getItem().getItemStack();
            tracker.fire(player, Quest.Goal.PICKUP, stack.getType().name(), stack.getAmount());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        tracker.fire(event.getPlayer(), Quest.Goal.EAT, event.getItem().getType().name());
    }

    /**
     * A harmful effect landing on the player, however it got there - drunk, eaten, splashed or
     * bitten on. Watching the effect rather than the source is what lets 독 효과 얻기 be one
     * rule covering a spider eye, a pufferfish and a cave spider alike; the arena has no brewing
     * stand without a nether, so an actual potion is the least likely of the three.
     *
     * <p>Only harmful ones are reported, so a quest that names no effect still cannot be
     * finished by drinking something good.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDebuff(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) {
            return;
        }
        PotionEffectType type = event.getNewEffect().getType();
        if (type.getCategory() == PotionEffectTypeCategory.HARMFUL) {
            tracker.fire(player, Quest.Goal.DEBUFF, type.getKey().getKey().toUpperCase(Locale.ROOT));
        }
    }

    // ---------- what is being worn ----------

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        if (!tracker.counts(player)) {
            return;
        }
        int worn = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && !piece.getType().isAir()) {
                worn++;
            }
        }
        tracker.fire(player, Quest.Goal.WEAR_ARMOR, null, worn);
        if (isDyedLeatherHelmet(player.getInventory().getHelmet())) {
            tracker.fire(player, Quest.Goal.WEAR_DYED_HELMET, null);
        }
    }

    /** Dyed, not merely leather: an undyed helmet still reports the stock brown, so the test is
     * whether the colour has been overridden at all. */
    private static boolean isDyedLeatherHelmet(ItemStack helmet) {
        return helmet != null && helmet.getType() == Material.LEATHER_HELMET
                && helmet.getItemMeta() instanceof LeatherArmorMeta meta
                && !meta.getColor().equals(Bukkit.getItemFactory().getDefaultLeatherColor());
    }

    // ---------- gestures ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Never ignoreCancelled here: right-clicking thin air arrives cancelled, and the
        // adventure-mode lobby cancels block clicks too.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && (block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE)) {
            extinguished(event.getPlayer());
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getMaterial() == Material.GLOW_INK_SAC
                && Tag.ALL_SIGNS.isTagged(block.getType())) {
            tracker.fire(event.getPlayer(), Quest.Goal.GLOW_SIGN, null);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !tracker.counts(player)) {
            return;
        }
        Block under = player.getLocation().getBlock().getRelative(0, -1, 0);
        if (under.getType() != Material.LIGHTNING_ROD) {
            return;
        }
        tracker.fire(player, Quest.Goal.SNEAK_ON_ROD, null);
        // Pure theatre: the rod answering is the point, so it must not hurt anyone.
        player.getWorld().strikeLightningEffect(under.getLocation().add(0.5, 0, 0.5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (event.getEntity() instanceof Wolf && event.getOwner() instanceof Player player) {
            tracker.fire(player, Quest.Goal.TAME_WOLF, null);
        }
    }

    /** Feeding counts the same as killing for 서로 다른 종류의 몹 - any animal taking food from
     * the hand is a kind you have met. */
    @EventHandler(ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Animals animal)) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (held.getType().isEdible() || animal.isBreedItem(held)) {
            tracker.fire(event.getPlayer(), Quest.Goal.DISTINCT_MOB, animal.getType().name());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSummonHealed(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        UUID owner = summons.ownerOf(mob);
        if (owner == null) {
            return;
        }
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            tracker.fire(player, Quest.Goal.SUMMON_HEAL, null, (int) Math.ceil(event.getAmount()));
        }
    }

    private void extinguished(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastExtinguish.put(player.getUniqueId(), now);
        if (last == null || now - last > EXTINGUISH_DEBOUNCE_MILLIS) {
            tracker.fire(player, Quest.Goal.EXTINGUISH, null);
        }
    }

    private static boolean isSword(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }
}
