package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class HelpCommand implements CommandExecutor {

    private record Entry(String command, String description) {}

    private static final List<Entry> COMMANDS = List.of(
            new Entry("/땅 구매 <이름>", "빈 땅문서 구매 (들고 우클릭하면 서 있는 구역 등록)"),
            new Entry("/땅 문서", "보유한 땅 목록 보기 · 클릭하면 땅문서 사본 발급"),
            new Entry("/땅 파기", "들고 있는 땅문서의 땅 포기"),
            new Entry("/땅 이름변경 <이름>", "들고 있는 내 땅문서의 이름 변경"),
            new Entry("/크레딧", "내 크레딧 잔액 확인"),
            new Entry("/크레딧 출금 <숫자>", "크레딧을 아이템으로 인출 (우클릭 시 환원)"),
            new Entry("/크레딧 송금 <플레이어> <숫자>", "다른 플레이어에게 크레딧 보내기"),
            new Entry("/직업", "직업 선택 / 내 직업 프로필 · 업그레이드"),
            new Entry("/상점", "서버 상점 (구매 · 직업별 특수구매)"),
            new Entry("/거래 <플레이어>", "다른 플레이어와 직접 거래"),
            new Entry("/거래소", "거래소 열기 (등록 · 구매 · 거래현황)"),
            new Entry("/귓속말 <플레이어> <메시지>", "귓속말 보내기 (다른 서버에 있어도 됨)"),
            new Entry("/파밍상자", "서버 간 공유 개인 상자"),
            new Entry("/파밍이동", "파밍 서버 ↔ 메인 서버 이동")
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text("=== 명령어 목록 ===", NamedTextColor.GOLD));
        for (Entry entry : COMMANDS) {
            sender.sendMessage(Component.text(entry.command(), NamedTextColor.AQUA)
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(entry.description(), NamedTextColor.WHITE)));
        }
        return true;
    }
}
