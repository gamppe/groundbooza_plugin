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
    private final Perks perks;
    private final StrengthPotionItem potions;

    public ClassController(MagicWarPlugin plugin, ClassManager classes, QuestManager quests,
                           ClassGuideItem guide, SkillItem skills, Perks perks,
                           StrengthPotionItem potions) {
        this.plugin = plugin;
        this.classes = classes;
        this.quests = quests;
        this.guide = guide;
        this.skills = skills;
        this.perks = perks;
        this.potions = potions;
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
            if (!magicClass.bonus().text().isEmpty()) {
                lore.add("클래스 특성: " + magicClass.bonus().text());
            }
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
            boolean ready = quests.isComplete(player.getUniqueId(), option.quest());
            List<String> lore = new ArrayList<>();
            lore.add(base.label() + " 의 2차 클래스");
            lore.add("");
            lore.add("두번째 스킬: " + option.skill().name());
            lore.add("전직 보너스: " + option.bonus().text());
            lore.add("");
            lore.add(option.quest().display() + " ("
                    + quests.count(player.getUniqueId(), option.quest()) + "/" + option.quest().target() + ")");
            lore.add(ready ? "클릭하여 전직" : "퀘스트를 완료해야 전직할 수 있습니다");
            inv.setItem(ClassSelectHolder.slotFor(i, options.size()),
                    icon(ready ? option.icon() : Material.BARRIER, option.label(),
                            ready ? option.color() : NamedTextColor.DARK_GRAY, lore));
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
        award(player, magicClass.bonus());
        guide.refresh(player, classes.displayName(player.getUniqueId()));
        for (int i = 0; i < magicClass.skills().size(); i++) {
            player.getInventory().addItem(skills.create(magicClass.skill(i), i, magicClass.label()));
        }
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        player.sendMessage(Component.text(magicClass.label() + " 클래스를 선택했습니다.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTask(plugin, () -> openBoard(player));
    }

    public void advance(Player player, int index) {
        UUID uuid = player.getUniqueId();
        MagicClass base = classes.classOf(uuid);
        if (base == null || classes.hasAdvanced(uuid)) {
            return;
        }
        List<MagicClass.Advancement> options = base.advancements();
        if (index < 0 || index >= options.size()) {
            return;
        }
        MagicClass.Advancement picked = options.get(index);
        // Each 전직 has its own quest; finishing one does not open the others.
        if (!quests.isComplete(uuid, picked.quest())) {
            player.sendMessage(Component.text(picked.quest().display() + " 을(를) 먼저 완료해야 합니다.",
                    NamedTextColor.RED));
            return;
        }
        classes.advance(uuid, picked);
        award(player, picked.bonus());
        guide.refresh(player, classes.displayName(uuid));
        player.getInventory().addItem(skills.create(picked.skill(),
                base.skills().size(), picked.label()));
        // The advancement may have renamed a skill they already carry.
        skills.refreshCarried(player, classes.ownedSkills(uuid), picked.label());
        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(Component.text(picked.label() + " (으)로 전직했습니다! 두번째 스킬을 얻었습니다.",
                NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("전직 보너스: " + picked.bonus().text(), NamedTextColor.LIGHT_PURPLE));
        Bukkit.getScheduler().runTask(plugin, () -> openBoard(player));
    }

    /** The anvil opens as soon as any one 전직 quest is done; which options are actually
     * takeable is decided inside the screen, per advancement. */
    public boolean advancementUnlocked(UUID uuid) {
        MagicClass base = classes.classOf(uuid);
        return base != null && base.advancements().stream()
                .anyMatch(advancement -> quests.isComplete(uuid, advancement.quest()));
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

        inv.setItem(ClassUpgradeHolder.SLOT_SKILLS, icon(Material.PAPER, "스킬", NamedTextColor.AQUA, List.of(
                "지금까지 얻은 스킬 목록",
                "클릭하여 열기")));
        inv.setItem(ClassUpgradeHolder.SLOT_CLASS, icon(
                classes.hasAdvanced(uuid) ? classes.advancementOf(uuid).icon() : magicClass.icon(),
                classes.displayName(uuid), NamedTextColor.GOLD, List.of(magicClass.blurb())));

        List<Quest> board = QuestManager.boardFor(magicClass, classes.advancementOf(uuid));
        for (int i = 0; i < board.size(); i++) {
            Quest quest = board.get(i);
            inv.setItem(ClassUpgradeHolder.questSlot(i), questIcon(uuid, quest));
            inv.setItem(ClassUpgradeHolder.woolSlot(i), questWool(uuid, quest));
        }
        inv.setItem(ClassUpgradeHolder.SLOT_ADVANCE, advanceIcon(uuid, magicClass));

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    /** Every skill earned so far. Clicking one hands over another copy; shift-clicking stamps
     * it onto whatever is in the player's hand instead. */
    public void openSkills(Player player) {
        List<ClassSkill> owned = classes.ownedSkills(player.getUniqueId());
        SkillListHolder holder = new SkillListHolder();
        Inventory inv = Bukkit.createInventory(holder, SkillListHolder.SIZE, Component.text("스킬 목록"));
        for (int i = 0; i < owned.size(); i++) {
            ClassSkill skill = owned.get(i);
            inv.setItem(SkillListHolder.slotFor(i), icon(skill.icon(), "[" + (i + 1) + "] " + skill.name(),
                    NamedTextColor.AQUA, List.of(
                            "쿨타임 " + skill.cooldownSeconds() + "초",
                            "",
                            "클릭하여 주문서(복사본) 받기",
                            "왼손에 주문서, 오른손에 아이템을 들고",
                            "우클릭하면 그 아이템에 스킬이 부여됩니다")));
        }
        inv.setItem(SkillListHolder.SLOT_BACK, icon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW, List.of()));
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void giveSkillCopy(Player player, int index) {
        List<ClassSkill> owned = classes.ownedSkills(player.getUniqueId());
        if (index < 0 || index >= owned.size()) {
            return;
        }
        ClassSkill skill = owned.get(index);
        player.getInventory().addItem(skills.create(skill, index, classes.displayName(player.getUniqueId())));
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
        player.sendMessage(Component.text(skill.name() + " 복사본을 받았습니다.", NamedTextColor.AQUA));
    }

    /** Off-hand scroll plus main-hand target: stamps the skill onto the target and spends the
     * scroll. The target keeps everything it had - the skill is tags, a cooldown group and two
     * lore lines on top. */
    public void bindSkill(Player player, ItemStack scroll, ItemStack target) {
        String skillId = skills.skillIdOf(scroll);
        ClassSkill skill = skillId == null ? null : classes.skillById(player.getUniqueId(), skillId);
        if (skill == null) {
            return;
        }
        int slot = classes.ownedSkills(player.getUniqueId()).indexOf(skill);
        skills.bind(target, skill, Math.max(0, slot), classes.displayName(player.getUniqueId()));
        scroll.setAmount(scroll.getAmount() - 1);

        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        player.sendMessage(Component.text(skill.name() + " 을(를) 아이템에 부여했습니다. 우클릭으로 사용하세요.",
                NamedTextColor.AQUA));
        if (hasOwnRightClick(target)) {
            player.sendMessage(Component.text("주의: 이 아이템의 원래 우클릭 동작은 더 이상 작동하지 않습니다.",
                    NamedTextColor.YELLOW));
        }
    }

    /** Casting cancels the interaction, so anything the item used to do on right-click - being
     * placed, eaten, drawn, thrown - stops working once a skill is on it. Worth saying out loud
     * at the moment of binding rather than letting the player discover it mid-fight. */
    private static boolean hasOwnRightClick(ItemStack item) {
        Material type = item.getType();
        return type.isBlock() || type.isEdible() || SELF_USING.contains(type);
    }

    private static final java.util.Set<Material> SELF_USING = java.util.Set.of(
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD, Material.FISHING_ROD,
            Material.ENDER_PEARL, Material.EGG, Material.SNOWBALL, Material.SPLASH_POTION,
            Material.LINGERING_POTION, Material.POTION, Material.MILK_BUCKET, Material.WATER_BUCKET,
            Material.LAVA_BUCKET, Material.BUCKET, Material.WRITTEN_BOOK, Material.MAP, Material.SPYGLASS,
            Material.GOAT_HORN, Material.FIREWORK_ROCKET, Material.CHORUS_FRUIT, Material.WIND_CHARGE);

    /** Clicking either a quest icon or its wool tries to claim - the icon is the obvious target
     * even though the wool is what changes colour. */
    public void claim(Player player, int index) {
        UUID uuid = player.getUniqueId();
        List<Quest> board = QuestManager.boardFor(classes.classOf(uuid), classes.advancementOf(uuid));
        if (index < 0 || index >= board.size()) {
            return;
        }
        Quest quest = board.get(index);
        if (quest.isPlaceholder()) {
            player.sendMessage(Component.text("아직 준비되지 않은 퀘스트입니다.", NamedTextColor.DARK_GRAY));
            return;
        }
        if (!quests.isComplete(uuid, quest)) {
            player.sendMessage(Component.text("아직 완료하지 않은 퀘스트입니다. ("
                    + quests.count(uuid, quest) + "/" + quest.target() + ")", NamedTextColor.RED));
            return;
        }
        if (!quests.claim(uuid, quest)) {
            player.sendMessage(Component.text("이미 보상을 받았습니다.", NamedTextColor.RED));
            return;
        }
        award(player, quest.reward());
        player.sendMessage(Component.text("보상 획득: " + quest.reward().text(), NamedTextColor.GREEN));
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        openBoard(player);
    }

    /** Hands over one reward: the stats go into {@link Perks}, and the one reward that is an
     * item is handed over here rather than there, so Perks stays about numbers. */
    private void award(Player player, Reward reward) {
        perks.grant(player, reward);
        if (reward.grants().containsKey(Perks.STRENGTH_POTION)) {
            player.getInventory().addItem(potions.create());
        }
    }

    // ---------- icons ----------

    private ItemStack questIcon(UUID uuid, Quest quest) {
        int count = quests.count(uuid, quest);
        boolean done = count >= quest.target();
        List<String> lore = new ArrayList<>();
        lore.add("진행도: " + count + " / " + quest.target());
        lore.add("보상: " + quest.reward().text());
        lore.add("");
        lore.add(done ? (quests.isClaimed(uuid, quest) ? "보상을 받았습니다" : "클릭하여 보상 획득") : "진행 중");
        ItemStack item = icon(quest.icon(), quest.display(), quest.color(), lore);
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
        return icon(wool, name, color, List.of(quest.display(), "보상: " + quest.reward().text()));
    }

    private ItemStack advanceIcon(UUID uuid, MagicClass magicClass) {
        if (classes.hasAdvanced(uuid)) {
            return icon(Material.ANVIL, "전직 완료", NamedTextColor.DARK_GRAY,
                    List.of("이미 " + classes.displayName(uuid) + " (으)로 전직했습니다"));
        }
        List<String> lore = new ArrayList<>();
        lore.add(magicClass.label() + " 의 2차 클래스를 고릅니다");
        lore.add("");
        for (MagicClass.Advancement advancement : magicClass.advancements()) {
            lore.add((quests.isComplete(uuid, advancement.quest()) ? "✔ " : "✖ ") + advancement.label()
                    + " — " + advancement.quest().title() + " ("
                    + quests.count(uuid, advancement.quest()) + "/" + advancement.quest().target() + ")");
        }
        lore.add("");
        boolean any = advancementUnlocked(uuid);
        lore.add(any ? "클릭하여 전직" : "전직 퀘스트를 하나라도 완료해야 합니다");
        return icon(Material.ANVIL, any ? "전직" : "전직 (잠김)",
                any ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY, lore);
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
