package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Covers grief vectors that aren't plain block break/place:
 * - emptying item frames / stripping armor stands on someone else's land
 * - knocking down item frames/paintings on someone else's land
 * - hitting any entity (mobs, other players, animals) while it's standing on someone else's land
 * - anyone but the actual tamer harming someone's pet, anywhere
 */
public class EntityProtectionListener implements Listener {

    private final MainCorePlugin plugin;

    public EntityProtectionListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Resolves the actual attacking player, whether they hit directly or via a projectile (arrow, trident, etc). */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame) && !(event.getRightClicked() instanceof ArmorStand)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!plugin.getLandManager().canBuild(event.getRightClicked().getLocation(), player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("이곳은 다른 사람의 땅입니다.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player) || player.isOp()) {
            return;
        }
        if (!plugin.getLandManager().canBuild(event.getEntity().getLocation(), player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("이곳은 다른 사람의 땅입니다.", NamedTextColor.RED));
        }
    }

    // Any entity (mob, animal, other player) standing on someone else's land is off-limits to
    // everyone but that land's owner - regardless of who tamed/owns the entity itself.
    @EventHandler(ignoreCancelled = true)
    public void onDamageOnLand(EntityDamageByEntityEvent event) {
        if (!plugin.getPvpProtectionState().isEnabled()) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.isOp()) {
            return;
        }
        if (!plugin.getLandManager().canBuild(event.getEntity().getLocation(), attacker.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("이곳은 다른 사람의 땅입니다.", NamedTextColor.RED));
        }
    }

    // A tamed pet is protected from everyone but its actual tamer, even outside its owner's land.
    @EventHandler(ignoreCancelled = true)
    public void onDamagePet(EntityDamageByEntityEvent event) {
        if (!plugin.getPvpProtectionState().isEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Tameable tameable) || !tameable.isTamed()) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.isOp()) {
            return;
        }
        AnimalTamer owner = tameable.getOwner();
        if (owner != null && !owner.getUniqueId().equals(attacker.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("다른 사람의 길들인 동물입니다.", NamedTextColor.RED));
        }
    }
}
