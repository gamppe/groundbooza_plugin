package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

/**
 * Animals don't breed any more. Instead each one can be fed once a day (its normal breeding
 * food), and the meal is what makes it productive: a cow becomes milkable, a pig hands over a
 * truffle, a sheep regrows wool (grass no longer does), a chicken lays, a horse may get faster
 * or jump higher, a rabbit may drop a foot. Every "may" is scaled by 농부's 행운 track.
 * ServerBridge carries a copy (without the luck scaling) for farm-server.
 */
public class AnimalFeeding implements Listener {

    /** One meal per Minecraft day (world day number = full time / 24000), so sleeping through
     * the night resets it just like anything else that happens "tomorrow". */
    private static final long TICKS_PER_DAY = 24_000L;
    /** Second milking: 0% on its own, +20% per 행운 level added (like the pig). */
    public static final double COW_EXTRA_MILK_CHANCE = 0.0;
    public static final double CHICKEN_FEATHER_CHANCE = 0.20;
    public static final double HORSE_UPGRADE_CHANCE = 0.30;
    public static final double RABBIT_FOOT_CHANCE = 0.10;
    /** Second truffle: 0% on its own, +20% per 행운 level *added* (not multiplied like the rest). */
    public static final double PIG_EXTRA_TRUFFLE_CHANCE = 0.0;

    public static final double HORSE_MIN_SPEED = 0.1125;
    public static final double HORSE_MAX_SPEED = 0.3375;
    public static final double HORSE_SPEED_STEP = 0.0225;
    public static final double HORSE_MIN_JUMP = 0.4;
    public static final double HORSE_MAX_JUMP = 1.0;
    public static final double HORSE_JUMP_STEP = 0.06;

    private final MainCorePlugin plugin;
    private final NamespacedKey fedAtKey;
    private final NamespacedKey milkChargesKey;
    private final NamespacedKey truffleKey;
    private final Random random = new Random();

    public AnimalFeeding(MainCorePlugin plugin) {
        this.plugin = plugin;
        this.fedAtKey = new NamespacedKey(plugin, "fed_day");
        this.milkChargesKey = new NamespacedKey(plugin, "milk_charges");
        this.truffleKey = new NamespacedKey(plugin, "truffle");
    }

    // ---------- no breeding, no free wool, no free eggs ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoveMode(EntityEnterLoveModeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegrowWool(SheepRegrowWoolEvent event) {
        event.setCancelled(true); // wool only comes back from a meal now
    }

    @EventHandler(ignoreCancelled = true)
    public void onNaturalEgg(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Chicken && event.getItemDrop().getItemStack().getType() == Material.EGG) {
            event.setCancelled(true); // eggs only come from a meal now
        }
    }

    // ---------- feeding / milking ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals animal) || !animal.isAdult()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();

        if (animal instanceof Cow cow && hand.getType() == Material.BUCKET) {
            handleMilk(event, player, cow);
            return;
        }
        if (hand.getType().isAir() || !animal.isBreedItem(hand)) {
            return;
        }
        event.setCancelled(true); // never let vanilla start love mode
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // the main-hand event already handled it
        }
        long today = animal.getWorld().getFullTime() / TICKS_PER_DAY;
        long fedDay = animal.getPersistentDataContainer().getOrDefault(fedAtKey, PersistentDataType.LONG, Long.MIN_VALUE);
        if (fedDay == today) {
            player.sendActionBar(Component.text("오늘은 이미 먹이를 먹었습니다. 내일 다시 주세요.", NamedTextColor.GRAY));
            return;
        }
        animal.getPersistentDataContainer().set(fedAtKey, PersistentDataType.LONG, today);
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        animal.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, animal.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0);
        animal.getWorld().playSound(animal.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        applyMeal(player, animal);
    }

    private double luck(Player player) {
        MainDatabase.JobProfile profile = plugin.getJobManager().getProfile(player.getUniqueId());
        return profile != null && profile.job() == Job.FARMER ? FarmerAbilities.luckChance(profile.level(0)) : 0;
    }

    /** 행운 scales every chance multiplicatively: base × (1 + 20% × level). */
    private boolean roll(Player player, double baseChance) {
        return random.nextDouble() < baseChance * (1 + luck(player));
    }

    /** Additive variant: base + 20% × level (used where the base is 0). */
    private boolean rollAdditive(Player player, double baseChance) {
        return random.nextDouble() < baseChance + luck(player);
    }

    private void applyMeal(Player player, Animals animal) {
        if (animal instanceof Cow cow) {
            int charges = 1 + (rollAdditive(player, COW_EXTRA_MILK_CHANCE) ? 1 : 0);
            cow.getPersistentDataContainer().set(milkChargesKey, PersistentDataType.INTEGER, charges);
            player.sendMessage(Component.text("우유를 짤 수 있을 것 같습니다.", NamedTextColor.GREEN));
        } else if (animal instanceof Pig) {
            int truffles = 1 + (rollAdditive(player, PIG_EXTRA_TRUFFLE_CHANCE) ? 1 : 0);
            give(player, animal, createTruffle(truffles));
            player.sendMessage(Component.text(truffles > 1 ? "돼지가 트러플을 2개나 찾아냈습니다!" : "돼지가 트러플을 찾아냈습니다!",
                    NamedTextColor.GREEN));
        } else if (animal instanceof Sheep sheep) {
            sheep.setSheared(false);
            player.sendMessage(Component.text("양털이 다시 자랐습니다.", NamedTextColor.GREEN));
        } else if (animal instanceof Chicken) {
            give(player, animal, new ItemStack(Material.EGG));
            if (roll(player, CHICKEN_FEATHER_CHANCE)) {
                give(player, animal, new ItemStack(Material.FEATHER));
                player.sendMessage(Component.text("닭이 알과 깃털을 주었습니다!", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("닭이 알을 주었습니다.", NamedTextColor.GREEN));
            }
        } else if (animal instanceof Horse horse) {
            if (!roll(player, HORSE_UPGRADE_CHANCE)) {
                player.sendMessage(Component.text("말이 맛있게 먹었습니다.", NamedTextColor.GREEN));
                return;
            }
            // Pick speed or jump at random; if that one's capped, try the other.
            boolean raised = random.nextBoolean()
                    ? raiseSpeed(horse) || raiseJump(horse)
                    : raiseJump(horse) || raiseSpeed(horse);
            if (!raised) {
                player.sendMessage(Component.text("말이 이미 최고 능력치입니다.", NamedTextColor.YELLOW));
                return;
            }
            player.sendMessage(Component.text(String.format("말의 능력이 올랐습니다! 속도 %.4f / 점프력 %.2f",
                    horseSpeed(horse), horse.getJumpStrength()), NamedTextColor.GREEN));
        } else if (animal instanceof Rabbit) {
            if (roll(player, RABBIT_FOOT_CHANCE)) {
                give(player, animal, new ItemStack(Material.RABBIT_FOOT));
                player.sendMessage(Component.text("토끼가 토끼발을 주었습니다!", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("토끼가 맛있게 먹었습니다.", NamedTextColor.GREEN));
            }
        } else {
            player.sendMessage(Component.text("맛있게 먹었습니다.", NamedTextColor.GREEN));
        }
    }

    private void handleMilk(PlayerInteractEntityEvent event, Player player, Cow cow) {
        int charges = cow.getPersistentDataContainer().getOrDefault(milkChargesKey, PersistentDataType.INTEGER, 0);
        if (charges <= 0) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                player.sendActionBar(Component.text("먹이를 먹은 소만 우유를 줍니다.", NamedTextColor.GRAY));
            }
            return;
        }
        // Let vanilla do the actual bucket swap; just burn one charge.
        cow.getPersistentDataContainer().set(milkChargesKey, PersistentDataType.INTEGER, charges - 1);
        if (charges - 1 > 0 && event.getHand() == EquipmentSlot.HAND) {
            player.sendMessage(Component.text("아직 더 짤 수 있을 것 같습니다.", NamedTextColor.GREEN));
        }
    }

    // ---------- horse stats ----------

    public static double horseSpeed(Horse horse) {
        AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        return speed == null ? HORSE_MIN_SPEED : speed.getBaseValue();
    }

    private static boolean raiseSpeed(Horse horse) {
        AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null || speed.getBaseValue() >= HORSE_MAX_SPEED - 1e-9) {
            return false;
        }
        speed.setBaseValue(Math.min(HORSE_MAX_SPEED, speed.getBaseValue() + HORSE_SPEED_STEP));
        return true;
    }

    private static boolean raiseJump(Horse horse) {
        if (horse.getJumpStrength() >= HORSE_MAX_JUMP - 1e-9) {
            return false;
        }
        horse.setJumpStrength(Math.min(HORSE_MAX_JUMP, horse.getJumpStrength() + HORSE_JUMP_STEP));
        return true;
    }

    // ---------- truffle ----------

    public ItemStack createTruffle() {
        return createTruffle(1);
    }

    public ItemStack createTruffle(int amount) {
        ItemStack item = new ItemStack(Material.BROWN_MUSHROOM, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("트러플", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("돼지가 찾아낸 귀한 버섯", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("비싸게 팔 수 있습니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(truffleKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTruffle(ItemStack item) {
        return item != null && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(truffleKey, PersistentDataType.BOOLEAN));
    }

    private static void give(Player player, Animals from, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            from.getWorld().dropItemNaturally(from.getLocation(), leftover);
        }
    }
}
