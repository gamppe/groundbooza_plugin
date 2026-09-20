package com.example.serverbridge;

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
 * Farm-server copy of MainCore's AnimalFeeding (same PDC keys under the literal "maincore"
 * namespace so a fed animal reads the same either way); 농부 행운 scaling comes from the
 * JobCache-backed FarmerAbilities. Only registered when MainCore isn't present.
 */
public class AnimalFeeding implements Listener {

    private static final long TICKS_PER_DAY = 24_000L;
    private static final double COW_EXTRA_MILK_CHANCE = 0.0; // + 행운 (additive)
    private static final double CHICKEN_FEATHER_CHANCE = 0.20;
    private static final double HORSE_UPGRADE_CHANCE = 0.30;
    private static final double RABBIT_FOOT_CHANCE = 0.10;
    private static final double PIG_EXTRA_TRUFFLE_CHANCE = 0.0; // + 행운 (additive)
    private static final double HORSE_MAX_SPEED = 0.3375;
    private static final double HORSE_SPEED_STEP = 0.0225;
    private static final double HORSE_MAX_JUMP = 1.0;
    private static final double HORSE_JUMP_STEP = 0.06;

    private static final NamespacedKey FED_AT_KEY = new NamespacedKey("maincore", "fed_day");
    private static final NamespacedKey MILK_CHARGES_KEY = new NamespacedKey("maincore", "milk_charges");
    private static final NamespacedKey TRUFFLE_KEY = new NamespacedKey("maincore", "truffle");

    private final Random random = new Random();
    private final FarmerAbilities farmer;

    public AnimalFeeding(FarmerAbilities farmer) {
        this.farmer = farmer;
    }

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
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNaturalEgg(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Chicken && event.getItemDrop().getItemStack().getType() == Material.EGG) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals animal) || !animal.isAdult()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();

        if (animal instanceof Cow cow && hand.getType() == Material.BUCKET) {
            int charges = cow.getPersistentDataContainer().getOrDefault(MILK_CHARGES_KEY, PersistentDataType.INTEGER, 0);
            if (charges <= 0) {
                event.setCancelled(true);
                if (event.getHand() == EquipmentSlot.HAND) {
                    player.sendActionBar(Component.text("먹이를 먹은 소만 우유를 줍니다.", NamedTextColor.GRAY));
                }
                return;
            }
            cow.getPersistentDataContainer().set(MILK_CHARGES_KEY, PersistentDataType.INTEGER, charges - 1);
            if (charges - 1 > 0 && event.getHand() == EquipmentSlot.HAND) {
                player.sendMessage(Component.text("아직 더 짤 수 있을 것 같습니다.", NamedTextColor.GREEN));
            }
            return;
        }
        if (hand.getType().isAir() || !animal.isBreedItem(hand)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        long today = animal.getWorld().getFullTime() / TICKS_PER_DAY;
        long fedDay = animal.getPersistentDataContainer().getOrDefault(FED_AT_KEY, PersistentDataType.LONG, Long.MIN_VALUE);
        if (fedDay == today) {
            player.sendActionBar(Component.text("오늘은 이미 먹이를 먹었습니다. 내일 다시 주세요.", NamedTextColor.GRAY));
            return;
        }
        animal.getPersistentDataContainer().set(FED_AT_KEY, PersistentDataType.LONG, today);
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        animal.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, animal.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0);
        animal.getWorld().playSound(animal.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        applyMeal(player, animal);
    }

    private boolean roll(Player player, double chance) {
        return random.nextDouble() < chance * farmer.luckMultiplier(player);
    }

    private boolean rollAdditive(Player player, double chance) {
        return random.nextDouble() < chance + (farmer.luckMultiplier(player) - 1);
    }

    private void applyMeal(Player player, Animals animal) {
        if (animal instanceof Cow cow) {
            int charges = 1 + (rollAdditive(player, COW_EXTRA_MILK_CHANCE) ? 1 : 0);
            cow.getPersistentDataContainer().set(MILK_CHARGES_KEY, PersistentDataType.INTEGER, charges);
            player.sendMessage(Component.text("우유를 짤 수 있을 것 같습니다.", NamedTextColor.GREEN));
        } else if (animal instanceof Pig) {
            int truffles = 1 + (rollAdditive(player, PIG_EXTRA_TRUFFLE_CHANCE) ? 1 : 0);
            ItemStack truffle = createTruffle();
            truffle.setAmount(truffles);
            give(player, animal, truffle);
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
            boolean raised = random.nextBoolean()
                    ? raiseSpeed(horse) || raiseJump(horse)
                    : raiseJump(horse) || raiseSpeed(horse);
            if (!raised) {
                player.sendMessage(Component.text("말이 이미 최고 능력치입니다.", NamedTextColor.YELLOW));
                return;
            }
            AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            player.sendMessage(Component.text(String.format("말의 능력이 올랐습니다! 속도 %.4f / 점프력 %.2f",
                    speed == null ? 0 : speed.getBaseValue(), horse.getJumpStrength()), NamedTextColor.GREEN));
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

    private static ItemStack createTruffle() {
        ItemStack item = new ItemStack(Material.BROWN_MUSHROOM);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("트러플", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("돼지가 찾아낸 귀한 버섯", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("비싸게 팔 수 있습니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(TRUFFLE_KEY, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private static void give(Player player, Animals from, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            from.getWorld().dropItemNaturally(from.getLocation(), leftover);
        }
    }
}
