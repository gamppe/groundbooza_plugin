package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/** Gives brand-new players (Bukkit's own hasPlayedBefore() check - independent of our DB) a
 * starter kit: a guide book, credits, iron tools and food. */
public class StarterKitListener implements Listener {

    private static final long STARTER_CREDITS = 5000L;

    private final MainCorePlugin plugin;

    public StarterKitListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }
        giveKit(player);
    }

    /** Also callable directly (e.g. from a debug command) to re-give the kit on demand. */
    public void giveKit(Player player) {
        player.getInventory().addItem(
                new ItemStack(Material.IRON_PICKAXE),
                new ItemStack(Material.IRON_SHOVEL),
                new ItemStack(Material.IRON_AXE),
                new ItemStack(Material.BREAD, 20),
                buildGuideBook()
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long updated = plugin.getMainDatabase().addBalance(player.getUniqueId(), STARTER_CREDITS);
            plugin.getEconomyCache().set(player.getUniqueId(), updated);
        });

        player.sendMessage(Component.text(
                "환영합니다! 초보자 지원 물품(가이드북, 크레딧 " + STARTER_CREDITS
                        + ", 철 도구, 빵 20개)을 받았습니다.",
                NamedTextColor.GREEN));
    }

    private ItemStack buildGuideBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("필독! 뉴비 가이드"));
        meta.author(Component.text("운영자"));
        meta.addPages(page1(), page2());
        for (String job : List.of("농부", "어부", "광부", "건축가", "모험가")) {
            meta.addPages(Component.text(job, NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("\n\n(자세한 설명 준비중)", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, true)));
        }
        book.setItemMeta(meta);
        return book;
    }

    private Component page1() {
        return Component.text("-- 땅 부자 타이쿤 --", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("\n\n여러분의 목표는\n개처럼 돈을 벌어,\n세계 최고의 ", NamedTextColor.BLACK))
                .append(Component.text("땅부자", NamedTextColor.GOLD, TextDecoration.BOLD, TextDecoration.ITALIC))
                .append(Component.text("가 되는 겁니다!\n\n먼저\n", NamedTextColor.BLACK))
                .append(Component.text("/땅 구매 (땅이름)", NamedTextColor.DARK_BLUE)
                        .decoration(TextDecoration.ITALIC, true)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.suggestCommand("/땅 구매 "))
                        .hoverEvent(HoverEvent.showText(Component.text("클릭해서 입력창에 채우기!")
                                .decoration(TextDecoration.ITALIC, true))))
                .append(Component.text("부터 \n시작하죠! 처음은 무료랍니다!\n\n다른 명령어들은 ", NamedTextColor.BLACK))
                .append(Component.text("/도움말", NamedTextColor.DARK_BLUE)
                        .decoration(TextDecoration.ITALIC, true)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.runCommand("/도움말")))
                .append(Component.text("을 이용해주세요!", NamedTextColor.BLACK));
    }

    private Component page2() {
        return Component.text("-- 직업 --", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("\n\n돈을 벌려면 게임에서도\n일을 해야죠!\n원하는 직업을 선택해보세요!\n직업은 ", NamedTextColor.BLACK))
                .append(Component.text("/직업", NamedTextColor.DARK_BLUE, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, true)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.runCommand("/직업"))
                        .hoverEvent(HoverEvent.showText(Component.text("클릭해서 보기!").decoration(TextDecoration.ITALIC, true))))
                .append(Component.text("으로 고르세요.\n(나중에 바꾸려면 수수료가 듭니다!)\n\n", NamedTextColor.BLACK))
                .append(jobLink("농부", 3)).append(Component.text("  "))
                .append(jobLink("어부", 4))
                .append(Component.text("\n"))
                .append(jobLink("광부", 5)).append(Component.text("  "))
                .append(jobLink("건축가", 6))
                .append(Component.text("\n"))
                .append(jobLink("모험가", 7));
    }

    private Component jobLink(String name, int page) {
        return Component.text(name, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, true)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.changePage(page));
    }
}
