package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Stream;

public class LandCommand implements CommandExecutor, TabCompleter {

    private final MainCorePlugin plugin;

    public LandCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (args.length == 0 || args[0].equals("?")) {
            player.sendMessage(Component.text("/땅 구매 <이름> - 빈 땅문서를 구매합니다. 들고 우클릭하면 서있는 구역을 등록합니다.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/땅 문서 - 보유한 모든 땅 목록을 봅니다. 클릭하면 땅문서 사본을 받습니다.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/땅 파기 - 들고 있는 땅문서의 소유권을 포기합니다.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/땅 이름변경 <이름> - 들고 있는 내 땅문서의 땅 이름을 바꿉니다.", NamedTextColor.GRAY));
            return true;
        }

        switch (args[0]) {
            case "구매" -> handleBuy(player, args);
            case "문서" -> handleDeedBook(player);
            case "파기" -> handleAbandon(player);
            case "이름변경" -> handleRename(player, args);
            default -> player.sendMessage(Component.text("알 수 없는 하위 명령어입니다. /땅 ? 를 확인하세요.", NamedTextColor.RED));
        }
        return true;
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /땅 이름변경 <새 이름>", NamedTextColor.RED));
            return;
        }
        String newName = args[1];
        if (newName.length() > 32) {
            player.sendMessage(Component.text("땅 이름은 32자 이내로 지어주세요.", NamedTextColor.RED));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        Integer landId = plugin.getDeedItem().getLandId(hand);
        if (landId == null) {
            player.sendMessage(Component.text("이름을 바꿀 땅문서를 손에 들고 입력해주세요.", NamedTextColor.RED));
            return;
        }
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (land == null || !land.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("효력이 없는 땅문서입니다.", NamedTextColor.RED));
            return;
        }

        String oldName = land.name();
        plugin.getLandManager().renameAsync(landId, newName, () -> {
            MainDatabase.Land updated = plugin.getLandManager().getLandById(landId);
            if (updated != null) {
                player.getInventory().setItemInMainHand(plugin.getDeedItem().create(updated, player.getName()));
            }
            player.sendMessage(Component.text(
                    "'" + oldName + "' 땅의 이름을 '" + newName + "'(으)로 변경했습니다.", NamedTextColor.GREEN));
        });
    }

    private void handleAbandon(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Integer landId = plugin.getDeedItem().getLandId(hand);
        boolean reservation = false;
        if (landId == null) {
            landId = plugin.getReservationDeedItem().getLandId(hand);
            reservation = landId != null;
        }
        if (landId == null) {
            player.sendMessage(Component.text("포기할 땅문서를 손에 들고 입력해주세요.", NamedTextColor.RED));
            return;
        }
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (reservation) {
            // A claimed 토지선점권 is "owned" by the RESERVATION_OWNER sentinel - the holder is
            // who may give it up, and only while it's not sitting in the marketplace.
            if (land == null || !land.reservation() || !player.getUniqueId().equals(land.holder())) {
                player.sendMessage(Component.text("효력이 없는 땅문서입니다.", NamedTextColor.RED));
                return;
            }
            if (land.owner().equals(MainDatabase.MARKET_OWNER)) {
                player.sendMessage(Component.text("거래소에 등록된 땅은 먼저 회수한 뒤 포기할 수 있습니다.", NamedTextColor.RED));
                return;
            }
        } else if (land == null || !land.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("효력이 없는 땅문서입니다.", NamedTextColor.RED));
            return;
        }

        plugin.getAbandonRequestManager().request(player.getUniqueId(), landId);
        player.sendMessage(Component.text("정말로 '" + land.name() + "' 땅을 포기하시겠습니까? ", NamedTextColor.AQUA)
                .append(Component.text("[✓]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_land_abandon_confirm")))
                .append(Component.text(" "))
                .append(Component.text("[✗]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_land_abandon_cancel"))));
    }

    private void handleBuy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /땅 구매 <이름>", NamedTextColor.RED));
            return;
        }
        String name = args[1];
        if (name.length() > 32) {
            player.sendMessage(Component.text("땅 이름은 32자 이내로 지어주세요.", NamedTextColor.RED));
            return;
        }
        if (plugin.getLandManager().countOwned(player.getUniqueId()) >= DeedBookHolder.SIZE) {
            player.sendMessage(Component.text("보유 가능한 땅 개수(최대 " + DeedBookHolder.SIZE + "개)를 초과할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }

        int owned = plugin.getLandManager().countOwned(player.getUniqueId());
        // 건축가 gets one extra free deed: their whole price ladder is shifted down by one, so
        // the 2nd is free too and the 3rd costs what everyone else's 2nd does.
        if (plugin.getJobManager().hasJob(player.getUniqueId(), Job.BUILDER)) {
            owned = Math.max(0, owned - 1);
        }
        long price = LandBuyListener.priceForNth(owned);
        plugin.getPendingPurchaseManager().request(player.getUniqueId(), name, price);

        player.sendMessage(Component.text("빈 땅문서 '" + name + "' 을 살까요? (가격: " + price + ") ", NamedTextColor.AQUA)
                .append(Component.text("[✓]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_land_buy_confirm")))
                .append(Component.text(" "))
                .append(Component.text("[✗]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_land_buy_cancel"))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Stream.of("구매", "문서", "파기", "이름변경").filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private void handleDeedBook(Player player) {
        List<MainDatabase.Land> owned = plugin.getLandManager().getOwned(player.getUniqueId());
        List<MainDatabase.PendingDeed> pending = plugin.getLandManager().getPendingDeeds(player.getUniqueId());
        DeedBookHolder holder = new DeedBookHolder(player.getUniqueId(), owned, pending);
        Inventory inv = Bukkit.createInventory(holder, DeedBookHolder.SIZE, Component.text("보유한 땅 목록"));

        int slot = 0;
        for (; slot < owned.size() && slot < DeedBookHolder.SIZE; slot++) {
            MainDatabase.Land land = owned.get(slot);
            ItemStack icon = land.reservation()
                    ? plugin.getReservationDeedItem().createClaimed(land)
                    : plugin.getDeedItem().create(land, player.getName());
            inv.setItem(slot, icon);
        }
        for (int i = 0; i < pending.size() && slot < DeedBookHolder.SIZE; i++, slot++) {
            MainDatabase.PendingDeed deed = pending.get(i);
            ItemStack icon = deed.reservation()
                    ? plugin.getReservationDeedItem().createBlank(deed)
                    : plugin.getPendingDeedItem().create(deed);
            inv.setItem(slot, icon);
        }

        holder.setInventory(inv);
        player.openInventory(inv);
    }
}
