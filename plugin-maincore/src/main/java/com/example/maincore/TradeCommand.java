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

import java.util.List;
import java.util.stream.Stream;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final MainCorePlugin plugin;

    public TradeCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length > 0 && args[0].equals("?")) {
            player.sendMessage(Component.text("/거래 - 내 거래창 열기", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/거래 <닉네임> - 거래 요청 보내기", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/거래 품목확인 - 상대방 거래창 보기(수정 불가)", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/거래 수락 - 거래 수락", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/거래 거절 - 거래 취소", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/거래 차단 - 거래 요청 차단 켜기/끄기", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/거래 거래소 - 거래소 열기", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/거래 등록 <가격> [\"메모\"] - 주 손에 든 아이템/땅문서를 거래소에 등록", NamedTextColor.GRAY));
            return true;
        }

        TradeManager tm = plugin.getTradeManager();

        if (args.length == 0) {
            int delivered = tm.collectMailbox(player);
            if (delivered > 0) {
                player.sendMessage(Component.text("보관 중이던 거래 아이템 " + delivered + "개를 받았습니다.", NamedTextColor.GREEN));
            }
            TradeSession session = tm.getSession(player.getUniqueId());
            if (session == null) {
                if (delivered == 0) {
                    player.sendMessage(Component.text("진행 중인 거래가 없습니다. /거래 <닉네임> 으로 먼저 요청하세요.", NamedTextColor.RED));
                }
                return true;
            }
            player.openInventory(session.ownInventory(player.getUniqueId()));
            return true;
        }

        switch (args[0]) {
            case "품목확인" -> {
                TradeSession session = tm.getSession(player.getUniqueId());
                if (session == null) {
                    player.sendMessage(Component.text("진행 중인 거래가 없습니다.", NamedTextColor.RED));
                    return true;
                }
                player.openInventory(session.otherInventory(player.getUniqueId()));
            }
            case "거절" -> {
                TradeSession session = tm.getSession(player.getUniqueId());
                if (session == null) {
                    player.sendMessage(Component.text("진행 중인 거래가 없습니다.", NamedTextColor.RED));
                    return true;
                }
                notifyBoth(session, Component.text("거래가 취소되었습니다.", NamedTextColor.RED));
                tm.cancelSession(session);
            }
            case "수락" -> {
                TradeSession session = tm.getSession(player.getUniqueId());
                if (session == null) {
                    player.sendMessage(Component.text("진행 중인 거래가 없습니다.", NamedTextColor.RED));
                    return true;
                }
                session.setOwnAccepted(player.getUniqueId(), true);
                player.sendMessage(Component.text("수락 대기 중입니다. 상대방도 수락하면 거래가 완료됩니다.", NamedTextColor.GREEN));
                Player other = Bukkit.getPlayer(session.other(player.getUniqueId()));
                if (other != null) {
                    other.sendMessage(Component.text(player.getName() + "님이 거래를 수락했습니다.", NamedTextColor.YELLOW));
                }
                if (session.bothAccepted()) {
                    notifyBoth(session, Component.text("거래가 완료되었습니다!", NamedTextColor.GREEN));
                    transferDeedOwnership(session);
                    tm.completeSession(session);
                }
            }
            case "차단" -> {
                boolean nowBlocked = tm.toggleBlocked(player.getUniqueId());
                player.sendMessage(Component.text(nowBlocked ? "거래 요청을 차단했습니다." : "거래 요청 차단을 해제했습니다.",
                        NamedTextColor.YELLOW));
            }
            case "거래소" -> plugin.getMarketController().openRoot(player);
            case "등록" -> handleRegister(player, args);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(Component.text("접속 중인 플레이어를 찾을 수 없습니다: " + args[0], NamedTextColor.RED));
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("자기 자신과는 거래할 수 없습니다.", NamedTextColor.RED));
                    return true;
                }
                if (tm.isBlocked(target.getUniqueId())) {
                    player.sendMessage(Component.text(target.getName() + "님은 거래를 받지 않는 상태입니다.", NamedTextColor.RED));
                    return true;
                }
                if (tm.getSession(player.getUniqueId()) != null || tm.getSession(target.getUniqueId()) != null) {
                    player.sendMessage(Component.text("이미 거래가 진행 중입니다.", NamedTextColor.RED));
                    return true;
                }
                TradeSession session = tm.startSession(player.getUniqueId(), target.getUniqueId());
                player.sendMessage(Component.text(target.getName() + "님에게 거래를 요청했습니다.", NamedTextColor.GREEN));
                player.openInventory(session.ownInventory(player.getUniqueId()));

                target.sendMessage(Component.text(player.getName() + "님이 거래를 요청했습니다. ", NamedTextColor.AQUA)
                        .append(Component.text("[품목확인]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/거래 품목확인")))
                        .append(Component.text(" "))
                        .append(Component.text("[거절]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/거래 거절"))));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        Stream<String> subcommands = Stream.of("품목확인", "수락", "거절", "차단", "거래소", "등록");
        Stream<String> playerNames = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !(sender instanceof Player p) || !name.equals(p.getName()));
        return Stream.concat(subcommands, playerNames)
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .toList();
    }

    private void handleRegister(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /거래 등록 <가격> [\"메모\"]", NamedTextColor.RED));
            return;
        }
        long price;
        try {
            price = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("가격은 숫자로 입력해주세요.", NamedTextColor.RED));
            return;
        }
        String memo = null;
        if (args.length > 2) {
            memo = String.join(" ", java.util.Arrays.asList(args).subList(2, args.length));
            memo = memo.replaceAll("^\"|\"$", "");
            if (memo.length() > 20) {
                memo = memo.substring(0, 20);
            }
        }
        plugin.getMarketController().registerFromHand(player, price, memo);
    }

    // Land deeds staged in each side's box change hands along with everything else - the actual
    // land ownership record in the DB has to move too, not just the physical item.
    private void transferDeedOwnership(TradeSession session) {
        transferDeedsFrom(session.invA, session.playerA, session.playerB);
        transferDeedsFrom(session.invB, session.playerB, session.playerA);
    }

    private void transferDeedsFrom(org.bukkit.inventory.Inventory staged, java.util.UUID seller, java.util.UUID newOwner) {
        for (org.bukkit.inventory.ItemStack item : staged.getContents()) {
            Integer landId = plugin.getDeedItem().getLandId(item);
            if (landId != null) {
                MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
                if (land != null) {
                    plugin.getLandManager().transferOwnershipAsync(landId, newOwner, null);
                }
                continue;
            }
            // A 토지선점권 (reservation deed) being traded is the sale itself - hand the recipient
            // real ownership now. The physical item stays reservation-styled until they
            // right-click it once to swap it for a proper LandDeedItem (see LandBuyListener).
            // Only the land's actual holder can trigger this - a dropped/otherwise-obtained copy
            // held by anyone else is effectively invalid, same as scrap paper.
            Integer reservationLandId = plugin.getReservationDeedItem().getLandId(item);
            if (reservationLandId != null) {
                MainDatabase.Land land = plugin.getLandManager().getLandById(reservationLandId);
                if (land != null && land.reservation() && seller.equals(land.holder())) {
                    plugin.getLandManager().convertReservationToNormalAsync(reservationLandId, newOwner, null);
                }
            }
        }
    }

    private void notifyBoth(TradeSession session, Component message) {
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);
        if (a != null) a.sendMessage(message);
        if (b != null) b.sendMessage(message);
    }
}
