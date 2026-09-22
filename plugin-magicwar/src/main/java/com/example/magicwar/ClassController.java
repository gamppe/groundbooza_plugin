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
import java.util.UUID;

/** Builds and drives the class screens: the opening pick, the quest board, and 전직. */
public class ClassController {

    private final MagicWarPlugin plugin;
    private final ClassManager classes;
    private final QuestManager quests;
    private final ClassGuideItem guide;
    private final SkillItem skills;

    public ClassController(MagicWarPlugin plugin, ClassManager classes, QuestManager quests,
                           ClassGuideItem guide, SkillItem skills) {
        this.plugin = plugin;
        this.classes = classes;
        this.quests = quests;
        this.guide = guide;
        this.skills = skills;
    }

    /** The guide, /클래스 and the round start all come through here: picker first, board once a
     * class exists. */
    public void open(Player player) {
        if (classes.hasClass(player.getUniqueId())) {
            openBoard(player);
        } else {
            openSelect(player);
        }
    }

    // ---------- picking a class ----------

    public void openSelect(Player player) {
        ClassSelectHolder holder = new ClassSelectHolder(false);
        Inventory inv = Bukkit.createInventory(holder, ClassSelectHolder.SIZE, Component.text("클래스 선택"));
        MagicClass[] options = MagicClass.values();
        for (int i = 0; i < options.length; i++) {
            MagicClass magicClass = options[i];
            List<String> lore = new ArrayList<>();
            lore.add(magicClass.blurb());
            lore.add("");
            lore.add("첫번째 스킬: " + magicClass.skill(0).name());
            lore.add("");
            lore.add("클릭하여 선택");
            inv.setItem(ClassSelectHolder.slotFor(i, options.length),
                    icon(magicClass.icon(), magicClass.label(), NamedTextColor.AQUA, lore));
        }
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    /** The anvil's screen: the tier-2 classes branching off whatever the player started as. */
    public void openAdvance(Player player) {
        MagicClass base = classes.classOf(player.getUniqueId());
        if (base == null) {
            openSelect(player);
            return;
        }
        ClassSelectHolder holder = new ClassSelectHolder(true);
        Inventory inv = Bukkit.createInventory(holder, ClassSelectHolder.SIZE, Component.text("전직"));
        List<MagicClass.Advancement> options = base.advancements();
        for (int i = 0; i < options.size(); i++) {
            MagicClass.Advancement option = options.get(i);
            inv.setItem(ClassSelectHolder.slotFor(i, options.size()),
                    icon(option.icon(), option.label(), NamedTextColor.LIGHT_PURPLE, List.of(
                            base.label() + " 의 2차 클래스",
                            "",
                            "두번째 스킬: " + option.skill().name(),
                            "",
                            "클릭하여 전직")));
        }
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void choose(Player player, int index) {
        MagicClass[] options = MagicClass.values();
        if (index < 0 || index >= options.length) {
            return;
        }
        MagicClass magicClass = options[index];
        classes.choose(player.getUniqueId(), magicClass);
        guide.refresh(player, classes.displayName(player.getUniqueId()));
        for (int i = 0; i < magicClass.skills().size(); i++) {
            player.getInventory().addItem(skills.create(magicClass, i));
        }
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        player.sendMessage(Component.text(magicClass.label() + " 클래스를 선택했습니다.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTask(plugin, () -> openBoard(player));
    }

    public void advance(Player player, int index) {
        UUID uuid = player.getUniqueId();
        MagicClass base = classes.classOf(uuid);
        if (base == null || classes.hasAdvanced(uuid) || !advancementUnlocked(uuid)) {
            return;
        }
        List<MagicClass.Advancement> options = base.advancements();
        if (index < 0 || index >= options.size()) {
            return;
        }
        MagicClass.Advancement picked = options.get(index);
        classes.advance(uuid, picked);
        guide.refresh(player, classes.displayName(uuid));
        player.getInventory().addItem(skills.createAdvanced(base, picked));
        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(Component.text(picked.label() + " (으)로 전직했습니다! 두번째 스킬을 얻었습니다.",
                NamedTextColor.LIGHT_PURPLE));
        Bukkit.getScheduler().runTask(plugin, () -> openBoard(player));
    }

    /** 전직 is gated on the 2차전직 quest being finished. */
    public boolean advancementUnlocked(UUID uuid) {
        Quest gate = QuestManager.advancementQuest();
        return gate != null && quests.isComplete(uuid, gate);
    }

    // ---------- the quest board ----------

    public void openBoard(Player player) {
        UUID uuid = player.getUniqueId();
        MagicClass magicClass = classes.classOf(uuid);
        if (magicClass == null) {
            openSelect(player);
            return;
        }
        ClassUpgradeHolder holder = new ClassUpgradeHolder();
        Inventory inv = Bukkit.createInventory(holder, ClassUpgradeHolder.SIZE,
                Component.text(classes.displayName(uuid) + " 클래스"));

        inv.setItem(ClassUpgradeHolder.SLOT_CLASS, icon(
                classes.hasAdvanced(uuid) ? classes.advancementOf(uuid).icon() : magicClass.icon(),
                classes.displayName(uuid), NamedTextColor.GOLD, List.of(magicClass.blurb())));

        for (int i = 0; i < QuestManager.BOARD.size(); i++) {
            Quest quest = QuestManager.BOARD.get(i);
            inv.setItem(ClassUpgradeHolder.questSlot(i), questIcon(uuid, quest));
            inv.setItem(ClassUpgradeHolder.woolSlot(i), questWool(uuid, quest));
        }
        inv.setItem(ClassUpgradeHolder.SLOT_ADVANCE, advanceIcon(uuid, magicClass));

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    /** Clicking either a quest icon or its wool tries to claim - the icon is the obvious target
     * even though the wool is what changes colour. */
    public void claim(Player player, int index) {
        UUID uuid = player.getUniqueId();
        Quest quest = QuestManager.BOARD.get(index);
        if (!quests.isComplete(uuid, quest)) {
            player.sendMessage(Component.text("아직 완료하지 않은 퀘스트입니다. ("
                    + quests.count(uuid, quest) + "/" + quest.target() + ")", NamedTextColor.RED));
            return;
        }
        if (!quests.claim(uuid, quest)) {
            player.sendMessage(Component.text("이미 보상을 받았습니다.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("보상 획득: " + quest.reward(), NamedTextColor.GREEN));
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        openBoard(player);
    }

    // ---------- icons ----------

    private ItemStack questIcon(UUID uuid, Quest quest) {
        int count = quests.count(uuid, quest);
        boolean done = count >= quest.target();
        List<String> lore = new ArrayList<>();
        lore.add("진행도: " + count + " / " + quest.target());
        lore.add("보상: " + quest.reward());
        lore.add("");
        lore.add(done ? (quests.isClaimed(uuid, quest) ? "보상을 받았습니다" : "클릭하여 보상 획득") : "진행 중");
        ItemStack item = icon(quest.icon(), quest.display(), quest.category().color(), lore);
        if (done) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Red while running, green once finished and claimable, dark grey after the reward. */
    private ItemStack questWool(UUID uuid, Quest quest) {
        int count = quests.count(uuid, quest);
        boolean done = count >= quest.target();
        boolean claimed = quests.isClaimed(uuid, quest);
        Material wool = claimed ? Material.GRAY_WOOL : done ? Material.LIME_WOOL : Material.RED_WOOL;
        String name = claimed ? "보상 획득 완료" : done ? "클릭하여 보상 획득" : "진행 " + count + " / " + quest.target();
        NamedTextColor color = claimed ? NamedTextColor.DARK_GRAY : done ? NamedTextColor.GREEN : NamedTextColor.RED;
        return icon(wool, name, color, List.of(quest.display(), "보상: " + quest.reward()));
    }

    private ItemStack advanceIcon(UUID uuid, MagicClass magicClass) {
        if (classes.hasAdvanced(uuid)) {
            return icon(Material.ANVIL, "전직 완료", NamedTextColor.DARK_GRAY,
                    List.of("이미 " + classes.displayName(uuid) + " (으)로 전직했습니다"));
        }
        Quest gate = QuestManager.advancementQuest();
        if (!advancementUnlocked(uuid)) {
            return icon(Material.ANVIL, "전직 (잠김)", NamedTextColor.DARK_GRAY, List.of(
                    gate == null ? "전직 퀘스트가 없습니다"
                            : gate.display() + " 을(를) 완료해야 합니다",
                    gate == null ? "" : "진행도: " + quests.count(uuid, gate) + " / " + gate.target()));
        }
        return icon(Material.ANVIL, "전직", NamedTextColor.LIGHT_PURPLE, List.of(
                magicClass.label() + " 의 2차 클래스를 고릅니다",
                "클릭하여 전직"));
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
