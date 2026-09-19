package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The /직업 menus and the per-job actions they expose. */
public class JobController {

    /** 경험치 1레벨 → 뼛가루 (농부). */
    private static final int BONE_MEAL_PER_LEVEL = 4;
    /** 원광 → 주괴 (광부), 1:1. */
    private static final Map<Material, Material> SMELTABLE = Map.of(
            Material.RAW_IRON, Material.IRON_INGOT,
            Material.RAW_GOLD, Material.GOLD_INGOT,
            Material.RAW_COPPER, Material.COPPER_INGOT);

    private record PendingChoice(Job job, boolean changing) {}

    private final MainCorePlugin plugin;
    private final Map<UUID, PendingChoice> pendingChoices = new ConcurrentHashMap<>();

    public JobController(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- menus ----------

    /** /직업 entry point: select screen if jobless, profile otherwise. */
    public void open(Player player) {
        if (plugin.getJobManager().getJob(player.getUniqueId()) == null) {
            openSelect(player, false);
        } else {
            openProfile(player);
        }
    }

    public void openSelect(Player player, boolean changing) {
        JobSelectHolder holder = new JobSelectHolder(changing);
        String title = changing ? "직업 변경 (수수료 " + String.format("%,d", JobManager.CHANGE_FEE) + " 크레딧)" : "직업 선택";
        Inventory inv = Bukkit.createInventory(holder, JobSelectHolder.SIZE, Component.text(title));
        Job current = plugin.getJobManager().getJob(player.getUniqueId());
        for (Job job : Job.values()) {
            List<String> lore = new ArrayList<>(job.perks());
            if (job == current) {
                lore.add("현재 직업");
            } else if (changing) {
                lore.add(String.format("클릭: %,d 크레딧을 내고 이 직업으로 변경", JobManager.CHANGE_FEE));
            } else {
                lore.add("클릭하여 선택");
            }
            ItemStack icon = icon(job.icon(), job.label(), job == current ? NamedTextColor.GREEN : NamedTextColor.GOLD, lore);
            inv.setItem(JobSelectHolder.slotFor(job), icon);
        }
        if (changing) {
            inv.setItem(JobSelectHolder.SLOT_BACK, icon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW, List.of()));
        }
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void openProfile(Player player) {
        MainDatabase.JobProfile profile = plugin.getJobManager().getProfile(player.getUniqueId());
        if (profile == null) {
            openSelect(player, false);
            return;
        }
        Job job = profile.job();

        JobProfileHolder holder = new JobProfileHolder();
        Inventory inv = Bukkit.createInventory(holder, JobProfileHolder.SIZE,
                Component.text("직업 프로필 - " + job.label()));

        List<String> card = new ArrayList<>(job.perks());
        card.add("칭호: " + JobManager.titleFor(profile.total()) + " (업그레이드 " + profile.total() + "개)");
        inv.setItem(JobProfileHolder.SLOT_JOB, icon(job.icon(), job.label(), NamedTextColor.GOLD, card));

        inv.setItem(JobProfileHolder.SLOT_UPGRADE, icon(Material.ANVIL, "업그레이드", NamedTextColor.AQUA, List.of(
                "클릭: 업그레이드 화면 열기",
                "총 " + profile.total() + " / " + (UpgradeTrack.MAX_LEVEL * job.tracks().size()) + "개 구매함")));

        ItemStack action = buildActionIcon(job);
        if (action != null) {
            inv.setItem(JobProfileHolder.SLOT_ACTION, action);
        }
        inv.setItem(JobProfileHolder.SLOT_SHOP, icon(Material.ENCHANTED_BOOK, job.label() + " 특수상점",
                NamedTextColor.LIGHT_PURPLE, List.of("클릭: 내 직업 전용 상점 열기")));
        inv.setItem(JobProfileHolder.SLOT_CHANGE, icon(Material.BARRIER, "직업 변경", NamedTextColor.RED, List.of(
                String.format("수수료 %,d 크레딧", JobManager.CHANGE_FEE),
                "업그레이드 단계는 초기화됩니다",
                "이전 직업의 도구는 사용할 수 없게 됩니다")));

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    // ---------- upgrade screen ----------

    public void openUpgrades(Player player) {
        MainDatabase.JobProfile profile = plugin.getJobManager().getProfile(player.getUniqueId());
        if (profile == null) {
            openSelect(player, false);
            return;
        }
        Job job = profile.job();
        JobUpgradeHolder holder = new JobUpgradeHolder();
        Inventory inv = Bukkit.createInventory(holder, JobUpgradeHolder.SIZE,
                Component.text(job.label() + " 업그레이드"));

        // Job card: what every track currently gives the player.
        List<String> card = new ArrayList<>();
        card.add("칭호: " + JobManager.titleFor(profile.total()));
        card.add("");
        for (int t = 0; t < job.tracks().size(); t++) {
            UpgradeTrack track = job.track(t);
            if (!track.available()) continue;
            int level = profile.level(t);
            card.add(track.label() + " " + level + "단계: " + track.effectAt(level));
        }
        inv.setItem(JobUpgradeHolder.SLOT_JOB, icon(job.icon(), job.label(), NamedTextColor.GOLD, card));

        for (int t = 0; t < job.tracks().size(); t++) {
            UpgradeTrack track = job.track(t);
            int level = profile.level(t);
            inv.setItem(JobUpgradeHolder.iconSlot(t), buildTrackIcon(track, level));
            for (int pip = 0; pip < UpgradeTrack.MAX_LEVEL; pip++) {
                inv.setItem(JobUpgradeHolder.pipSlot(t, pip), buildPip(track, level, pip < level));
            }
        }
        inv.setItem(JobUpgradeHolder.SLOT_BACK, icon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW, List.of()));

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    private ItemStack buildTrackIcon(UpgradeTrack track, int level) {
        List<String> lore = new ArrayList<>(track.description());
        if (!track.available()) {
            return icon(track.icon(), track.label(), NamedTextColor.DARK_GRAY, lore);
        }
        lore.add("");
        lore.add("현재 (" + level + "단계): " + track.effectAt(level));
        long price = UpgradeTrack.price(level);
        if (level >= UpgradeTrack.MAX_LEVEL || price < 0) {
            lore.add("최대 단계입니다");
            return icon(track.icon(), track.label() + " (" + level + " / " + UpgradeTrack.MAX_LEVEL + ")", NamedTextColor.GREEN, lore);
        }
        lore.add("다음 (" + (level + 1) + "단계): " + track.effectAt(level + 1));
        lore.add(String.format("클릭: %,d 크레딧으로 업그레이드", price));
        return icon(track.icon(), track.label() + " (" + level + " / " + UpgradeTrack.MAX_LEVEL + ")", NamedTextColor.AQUA, lore);
    }

    /** One wool pip: yellow once bought, dark gray otherwise. Hover shows "■■■ 3/5" (□ 0/5 at zero). */
    private ItemStack buildPip(UpgradeTrack track, int level, boolean filled) {
        String bar = level == 0 ? "□" : "■".repeat(level);
        String name = bar + " " + level + " / " + UpgradeTrack.MAX_LEVEL;
        Material wool = !track.available() ? Material.BLACK_WOOL : filled ? Material.YELLOW_WOOL : Material.GRAY_WOOL;
        return icon(wool, name, filled ? NamedTextColor.YELLOW : NamedTextColor.GRAY, List.of(track.label()));
    }

    private ItemStack buildActionIcon(Job job) {
        return switch (job) {
            case FARMER -> icon(Material.BONE_MEAL, "경험치 → 뼛가루", NamedTextColor.GREEN, List.of(
                    "경험치 1레벨당 뼛가루 " + BONE_MEAL_PER_LEVEL + "개",
                    "클릭: 1레벨 전환", "쉬프트 클릭: 전부 전환"));
            case MINER -> icon(Material.IRON_INGOT, "원광 → 주괴", NamedTextColor.GREEN, List.of(
                    "인벤토리의 철/금/구리 원광을 주괴로 바꿉니다 (1:1)",
                    "클릭: 전부 전환"));
            default -> null;
        };
    }

    // ---------- choosing (chat confirm, same pattern as /땅 구매) ----------

    public void requestChoice(Player player, Job job, boolean changing) {
        UUID uuid = player.getUniqueId();
        Job current = plugin.getJobManager().getJob(uuid);
        if (changing && current == job) {
            player.sendMessage(Component.text("이미 " + job.label() + " 직업입니다.", NamedTextColor.RED));
            return;
        }
        if (!changing && current != null) {
            player.sendMessage(Component.text("이미 직업이 있습니다. 프로필에서 변경할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        pendingChoices.put(uuid, new PendingChoice(job, changing));
        player.closeInventory();
        String question = changing
                ? String.format("정말로 %s(으)로 직업을 변경할까요? (수수료 %,d 크레딧, 업그레이드 초기화) ", job.label(), JobManager.CHANGE_FEE)
                : "정말로 " + job.label() + " 직업을 선택할까요? (나중에 변경 시 수수료가 듭니다) ";
        player.sendMessage(Component.text(question, NamedTextColor.AQUA)
                .append(Component.text("[✓]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_job_confirm")))
                .append(Component.text(" "))
                .append(Component.text("[✗]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_job_cancel"))));
    }

    public void confirmChoice(Player player) {
        PendingChoice choice = pendingChoices.remove(player.getUniqueId());
        if (choice == null) {
            player.sendMessage(Component.text("대기 중인 직업 선택이 없습니다.", NamedTextColor.RED));
            return;
        }
        Job job = choice.job();
        if (!choice.changing()) {
            plugin.getJobManager().chooseAsync(player, job, ok -> {
                if (!ok) {
                    player.sendMessage(Component.text("직업을 선택할 수 없습니다. (이미 직업이 있거나 처리 중)", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text(job.label() + " 직업을 선택했습니다! /직업 으로 프로필을 확인하세요.", NamedTextColor.GREEN));
            });
            return;
        }
        Job previous = plugin.getJobManager().getJob(player.getUniqueId());
        plugin.getJobManager().changeAsync(player, job, profile -> {
            if (profile == null) {
                return; // reason already messaged (or nothing to change)
            }
            player.sendMessage(Component.text(
                    String.format("직업을 %s(으)로 변경했습니다. (수수료 %,d 크레딧)", job.label(), JobManager.CHANGE_FEE),
                    NamedTextColor.GREEN));
            if (previous != null && plugin.getJobManager().carriesToolsOf(player.getInventory(), previous)) {
                player.sendMessage(Component.text(
                        "이전 직업(" + previous.label() + ")의 도구는 더 이상 사용할 수 없습니다.", NamedTextColor.YELLOW));
            }
        });
    }

    public void cancelChoice(Player player) {
        if (pendingChoices.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("직업 선택을 취소했습니다.", NamedTextColor.GRAY));
        }
    }

    // ---------- profile actions ----------

    public void upgrade(Player player, int track) {
        plugin.getJobManager().upgradeAsync(player, track, level -> {
            if (level < 0) {
                return;
            }
            MainDatabase.JobProfile profile = plugin.getJobManager().getProfile(player.getUniqueId());
            UpgradeTrack definition = profile.job().track(track);
            player.sendMessage(Component.text(
                    definition.label() + " " + level + "단계 완료! (" + definition.effectAt(level) + ")", NamedTextColor.GREEN));
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof JobUpgradeHolder) {
                openUpgrades(player); // refresh pips + numbers
            }
        });
    }

    public void runAction(Player player, boolean all) {
        Job job = plugin.getJobManager().getJob(player.getUniqueId());
        if (job == Job.FARMER) {
            convertXpToBoneMeal(player, all);
        } else if (job == Job.MINER) {
            smeltRawOres(player);
        }
    }

    private void convertXpToBoneMeal(Player player, boolean all) {
        int levels = all ? player.getLevel() : Math.min(1, player.getLevel());
        if (levels <= 0) {
            player.sendMessage(Component.text("전환할 경험치 레벨이 없습니다.", NamedTextColor.RED));
            return;
        }
        int boneMeal = levels * BONE_MEAL_PER_LEVEL;
        // Drop the progress bar too so partial levels can't be re-cashed by spending 1 level at a time.
        player.setLevel(player.getLevel() - levels);
        player.setExp(0f);
        giveStacked(player, Material.BONE_MEAL, boneMeal);
        player.sendMessage(Component.text("경험치 " + levels + "레벨을 뼛가루 " + boneMeal + "개로 전환했습니다.", NamedTextColor.GREEN));
    }

    private void smeltRawOres(Player player) {
        int converted = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            Material ingot = SMELTABLE.get(item.getType());
            if (ingot == null) continue;
            int amount = item.getAmount();
            player.getInventory().setItem(i, new ItemStack(ingot, amount));
            converted += amount;
        }
        if (converted == 0) {
            player.sendMessage(Component.text("인벤토리에 철/금/구리 원광이 없습니다.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("원광 " + converted + "개를 주괴로 전환했습니다.", NamedTextColor.GREEN));
    }

    private void giveStacked(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStack = new ItemStack(material).getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            for (ItemStack leftover : player.getInventory().addItem(new ItemStack(material, give)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= give;
        }
    }

    private ItemStack icon(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }
}
