package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Builds and drives the two class screens. Laid out to match MainCore's /직업 so the two
 * servers feel like the same game. */
public class ClassController {

    private final MagicWarPlugin plugin;
    private final ClassManager classes;
    private final ClassGuideItem guide;
    private final SkillItem skills;

    public ClassController(MagicWarPlugin plugin, ClassManager classes, ClassGuideItem guide, SkillItem skills) {
        this.plugin = plugin;
        this.classes = classes;
        this.guide = guide;
        this.skills = skills;
    }

    /** The guide, /클래스 and the round start all come through here: picker first, upgrades
     * once a class exists. */
    public void open(Player player) {
        if (classes.hasClass(player.getUniqueId())) {
            openUpgrades(player);
        } else {
            openSelect(player);
        }
    }

    public void openSelect(Player player) {
        ClassSelectHolder holder = new ClassSelectHolder();
        Inventory inv = Bukkit.createInventory(holder, ClassSelectHolder.SIZE, Component.text("클래스 선택"));
        for (MagicClass magicClass : MagicClass.values()) {
            List<String> lore = new ArrayList<>();
            lore.add(magicClass.blurb());
            lore.add("");
            for (ClassTrack track : magicClass.tracks()) {
                lore.add("· " + track.label());
            }
            lore.add("");
            lore.add("클릭하여 선택");
            inv.setItem(ClassSelectHolder.slotFor(magicClass),
                    icon(magicClass.icon(), magicClass.label(), NamedTextColor.AQUA, lore));
        }
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void openUpgrades(Player player) {
        MagicClass magicClass = classes.classOf(player.getUniqueId());
        if (magicClass == null) {
            openSelect(player);
            return;
        }
        ClassUpgradeHolder holder = new ClassUpgradeHolder();
        Inventory inv = Bukkit.createInventory(holder, ClassUpgradeHolder.SIZE,
                Component.text(magicClass.label() + " 강화"));

        List<String> card = new ArrayList<>();
        card.add(magicClass.blurb());
        card.add("");
        for (int t = 0; t < magicClass.tracks().size(); t++) {
            ClassTrack track = magicClass.track(t);
            if (!track.available()) {
                continue;
            }
            int level = classes.levelOf(player.getUniqueId(), t);
            card.add(track.label() + " " + level + "단계: " + track.effectAt(level));
        }
        inv.setItem(ClassUpgradeHolder.SLOT_CLASS,
                icon(magicClass.icon(), magicClass.label(), NamedTextColor.GOLD, card));

        for (int t = 0; t < magicClass.tracks().size(); t++) {
            ClassTrack track = magicClass.track(t);
            int level = classes.levelOf(player.getUniqueId(), t);
            inv.setItem(ClassUpgradeHolder.iconSlot(t), buildTrackIcon(track, level));
            for (int pip = 0; pip < ClassTrack.MAX_LEVEL; pip++) {
                inv.setItem(ClassUpgradeHolder.pipSlot(t, pip), buildPip(track, level, pip < level));
            }
        }
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void choose(Player player, MagicClass magicClass) {
        classes.choose(player.getUniqueId(), magicClass);
        guide.refresh(player, magicClass);
        for (int i = 0; i < magicClass.skills().size(); i++) {
            player.getInventory().addItem(skills.create(magicClass, i));
        }
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        player.sendMessage(Component.text(magicClass.label() + " 클래스를 선택했습니다.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTask(plugin, () -> openUpgrades(player));
    }

    public void upgrade(Player player, int track) {
        MagicClass magicClass = classes.classOf(player.getUniqueId());
        if (magicClass == null) {
            return;
        }
        if (!magicClass.track(track).available()) {
            player.sendMessage(Component.text("아직 준비 중인 강화입니다.", NamedTextColor.RED));
            return;
        }
        int level = classes.upgrade(player.getUniqueId(), track);
        if (level < 0) {
            player.sendMessage(Component.text("이미 최대 단계입니다.", NamedTextColor.RED));
            return;
        }
        ClassTrack definition = magicClass.track(track);
        player.sendMessage(Component.text(definition.label() + " " + level + "단계 완료! ("
                + definition.effectAt(level) + ")", NamedTextColor.GREEN));
        player.playSound(player, level >= ClassTrack.MAX_LEVEL
                ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        openUpgrades(player); // refresh pips + numbers
    }

    // ---------- icons ----------

    private ItemStack buildTrackIcon(ClassTrack track, int level) {
        List<String> lore = new ArrayList<>(track.description());
        if (!track.available()) {
            return icon(track.icon(), track.label(), NamedTextColor.DARK_GRAY, lore);
        }
        lore.add("");
        lore.add("현재 (" + level + "단계): " + track.effectAt(level));
        if (level >= ClassTrack.MAX_LEVEL) {
            lore.add("최대 단계입니다");
            return icon(track.icon(), track.label() + " (" + level + " / " + ClassTrack.MAX_LEVEL + ")",
                    NamedTextColor.GREEN, lore);
        }
        lore.add("다음 (" + (level + 1) + "단계): " + track.effectAt(level + 1));
        lore.add("클릭하여 강화");
        return icon(track.icon(), track.label() + " (" + level + " / " + ClassTrack.MAX_LEVEL + ")",
                NamedTextColor.AQUA, lore);
    }

    private ItemStack buildPip(ClassTrack track, int level, boolean filled) {
        String bar = level == 0 ? "□" : "■".repeat(level);
        String name = bar + " " + level + " / " + ClassTrack.MAX_LEVEL;
        Material wool = !track.available() ? Material.BLACK_WOOL : filled ? Material.YELLOW_WOOL : Material.GRAY_WOOL;
        return icon(wool, name, filled ? NamedTextColor.YELLOW : NamedTextColor.GRAY, List.of(track.label()));
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }
}
