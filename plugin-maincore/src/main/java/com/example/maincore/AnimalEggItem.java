package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 농부's spawn eggs, one per animal *variant* (warm/cold cows, every sheep colour, horse coats,
 * rabbit types...). They're real vanilla eggs carrying a kind+variant tag; AnimalSpawnListener
 * intercepts the right-click and spawns the exact variant itself instead of letting vanilla
 * roll a random one.
 */
public class AnimalEggItem {

    /** One purchasable egg: what to spawn and how to label it in the shop. */
    public record Variant(Material egg, String kind, String variant, String label) {}

    private static final Map<String, String> CLIMATE = new LinkedHashMap<>();
    private static final Map<DyeColor, String> SHEEP_COLORS = new LinkedHashMap<>();
    private static final Map<Horse.Color, String> HORSE_COLORS = new LinkedHashMap<>();
    private static final Map<Rabbit.Type, String> RABBIT_TYPES = new LinkedHashMap<>();
    static {
        CLIMATE.put("TEMPERATE", "일반");
        CLIMATE.put("WARM", "따뜻한 지역");
        CLIMATE.put("COLD", "추운 지역");
        SHEEP_COLORS.put(DyeColor.WHITE, "흰색");
        SHEEP_COLORS.put(DyeColor.LIGHT_GRAY, "회백색");
        SHEEP_COLORS.put(DyeColor.GRAY, "회색");
        SHEEP_COLORS.put(DyeColor.BLACK, "검은색");
        SHEEP_COLORS.put(DyeColor.BROWN, "갈색");
        SHEEP_COLORS.put(DyeColor.RED, "빨간색");
        SHEEP_COLORS.put(DyeColor.ORANGE, "주황색");
        SHEEP_COLORS.put(DyeColor.YELLOW, "노란색");
        SHEEP_COLORS.put(DyeColor.LIME, "연두색");
        SHEEP_COLORS.put(DyeColor.GREEN, "초록색");
        SHEEP_COLORS.put(DyeColor.CYAN, "청록색");
        SHEEP_COLORS.put(DyeColor.LIGHT_BLUE, "하늘색");
        SHEEP_COLORS.put(DyeColor.BLUE, "파란색");
        SHEEP_COLORS.put(DyeColor.PURPLE, "보라색");
        SHEEP_COLORS.put(DyeColor.MAGENTA, "자홍색");
        SHEEP_COLORS.put(DyeColor.PINK, "분홍색");
        HORSE_COLORS.put(Horse.Color.WHITE, "흰색");
        HORSE_COLORS.put(Horse.Color.CREAMY, "크림색");
        HORSE_COLORS.put(Horse.Color.CHESTNUT, "밤색");
        HORSE_COLORS.put(Horse.Color.BROWN, "갈색");
        HORSE_COLORS.put(Horse.Color.BLACK, "검은색");
        HORSE_COLORS.put(Horse.Color.GRAY, "회색");
        HORSE_COLORS.put(Horse.Color.DARK_BROWN, "진갈색");
        RABBIT_TYPES.put(Rabbit.Type.BROWN, "갈색");
        RABBIT_TYPES.put(Rabbit.Type.WHITE, "흰색");
        RABBIT_TYPES.put(Rabbit.Type.BLACK, "검은색");
        RABBIT_TYPES.put(Rabbit.Type.BLACK_AND_WHITE, "흑백");
        RABBIT_TYPES.put(Rabbit.Type.GOLD, "금색");
        RABBIT_TYPES.put(Rabbit.Type.SALT_AND_PEPPER, "얼룩");
    }

    /** Shop order: every variant of one animal before moving on to the next. */
    public static List<Variant> all() {
        List<Variant> out = new ArrayList<>();
        for (Map.Entry<String, String> c : CLIMATE.entrySet()) {
            out.add(new Variant(Material.COW_SPAWN_EGG, "COW", c.getKey(), "소 (" + c.getValue() + ")"));
        }
        for (Map.Entry<String, String> c : CLIMATE.entrySet()) {
            out.add(new Variant(Material.PIG_SPAWN_EGG, "PIG", c.getKey(), "돼지 (" + c.getValue() + ")"));
        }
        for (Map.Entry<String, String> c : CLIMATE.entrySet()) {
            out.add(new Variant(Material.CHICKEN_SPAWN_EGG, "CHICKEN", c.getKey(), "닭 (" + c.getValue() + ")"));
        }
        for (Map.Entry<DyeColor, String> c : SHEEP_COLORS.entrySet()) {
            out.add(new Variant(Material.SHEEP_SPAWN_EGG, "SHEEP", c.getKey().name(), "양 (" + c.getValue() + ")"));
        }
        for (Map.Entry<Horse.Color, String> c : HORSE_COLORS.entrySet()) {
            out.add(new Variant(Material.HORSE_SPAWN_EGG, "HORSE", c.getKey().name(), "말 (" + c.getValue() + ")"));
        }
        for (Map.Entry<Rabbit.Type, String> c : RABBIT_TYPES.entrySet()) {
            out.add(new Variant(Material.RABBIT_SPAWN_EGG, "RABBIT", c.getKey().name(), "토끼 (" + c.getValue() + ")"));
        }
        return out;
    }

    private final NamespacedKey kindKey;
    private final NamespacedKey variantKey;

    public AnimalEggItem(MainCorePlugin plugin) {
        this.kindKey = new NamespacedKey(plugin, "animal_egg_kind");
        this.variantKey = new NamespacedKey(plugin, "animal_egg_variant");
    }

    public ItemStack create(Variant variant) {
        ItemStack item = new ItemStack(variant.egg());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(variant.label() + " 스폰알", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("농부만 사용 가능", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("블록 우클릭: 이 종류의 동물 소환", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, variant.kind());
        meta.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, variant.variant());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isVariantEgg(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(kindKey, PersistentDataType.STRING);
    }

    /** Spawns the tagged variant at `where` (block-centred), or null if the tag is unreadable.
     * Horses come out tamed to `owner` with the lowest possible speed/jump - feeding is the only
     * way up from there (AnimalFeeding). */
    public LivingEntity spawn(ItemStack egg, Location where, Player owner) {
        String kind = egg.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        String variant = egg.getItemMeta().getPersistentDataContainer().getOrDefault(variantKey, PersistentDataType.STRING, "");
        if (kind == null) {
            return null;
        }
        CreatureSpawnEvent.SpawnReason reason = CreatureSpawnEvent.SpawnReason.SPAWNER_EGG;
        return switch (kind) {
            case "COW" -> where.getWorld().spawn(where, Cow.class, reason, true, cow -> cow.setVariant(climate(variant, Cow.Variant.TEMPERATE, Cow.Variant.WARM, Cow.Variant.COLD)));
            case "PIG" -> where.getWorld().spawn(where, Pig.class, reason, true, pig -> pig.setVariant(climate(variant, Pig.Variant.TEMPERATE, Pig.Variant.WARM, Pig.Variant.COLD)));
            case "CHICKEN" -> where.getWorld().spawn(where, Chicken.class, reason, true, chicken -> chicken.setVariant(climate(variant, Chicken.Variant.TEMPERATE, Chicken.Variant.WARM, Chicken.Variant.COLD)));
            case "SHEEP" -> where.getWorld().spawn(where, Sheep.class, reason, true, sheep -> sheep.setColor(parse(DyeColor.class, variant, DyeColor.WHITE)));
            case "HORSE" -> where.getWorld().spawn(where, Horse.class, reason, true, horse -> {
                horse.setColor(parse(Horse.Color.class, variant, Horse.Color.BROWN));
                AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) speed.setBaseValue(AnimalFeeding.HORSE_MIN_SPEED);
                horse.setJumpStrength(AnimalFeeding.HORSE_MIN_JUMP);
                horse.setTamed(true);
                horse.setOwner(owner);
            });
            case "RABBIT" -> where.getWorld().spawn(where, Rabbit.class, reason, true, rabbit -> rabbit.setRabbitType(parse(Rabbit.Type.class, variant, Rabbit.Type.BROWN)));
            default -> null;
        };
    }

    private static <T> T climate(String raw, T temperate, T warm, T cold) {
        return switch (raw) {
            case "WARM" -> warm;
            case "COLD" -> cold;
            default -> temperate;
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
