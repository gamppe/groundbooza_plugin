package com.example.maincore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class TradeSession {

    public static final int SIZE = 9;

    public final UUID playerA;
    public final UUID playerB;
    public final Inventory invA;
    public final Inventory invB;
    public boolean acceptedA = false;
    public boolean acceptedB = false;

    public TradeSession(UUID playerA, UUID playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.invA = Bukkit.createInventory(new TradeOwnHolder(this, playerA), SIZE, Component.text("거래 - 내 아이템"));
        this.invB = Bukkit.createInventory(new TradeOwnHolder(this, playerB), SIZE, Component.text("거래 - 내 아이템"));
    }

    public Inventory ownInventory(UUID player) {
        return player.equals(playerA) ? invA : invB;
    }

    public Inventory otherInventory(UUID player) {
        return player.equals(playerA) ? invB : invA;
    }

    public UUID other(UUID player) {
        return player.equals(playerA) ? playerB : playerA;
    }

    public boolean isOwnAccepted(UUID player) {
        return player.equals(playerA) ? acceptedA : acceptedB;
    }

    public void setOwnAccepted(UUID player, boolean value) {
        if (player.equals(playerA)) acceptedA = value; else acceptedB = value;
    }

    public void resetAccepted() {
        acceptedA = false;
        acceptedB = false;
    }

    public boolean bothAccepted() {
        return acceptedA && acceptedB;
    }
}
