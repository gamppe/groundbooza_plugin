package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CompassSetBiomeCommand implements CommandExecutor {

    private final SpecialToolListener specialToolListener;

    public CompassSetBiomeCommand(SpecialToolListener specialToolListener) {
        this.specialToolListener = specialToolListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) {
            return true;
        }
        Biome biome;
        try {
            biome = Biome.valueOf(args[0]);
        } catch (IllegalArgumentException e) {
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!specialToolListener.isSpecialCompass(hand)) {
            player.sendMessage(Component.text("나침반을 손에 들고 있어야 합니다.", NamedTextColor.RED));
            return true;
        }
        specialToolListener.setTargetBiome(hand, biome);
        specialToolListener.pointCompass(player, hand, biome);
        return true;
    }
}
