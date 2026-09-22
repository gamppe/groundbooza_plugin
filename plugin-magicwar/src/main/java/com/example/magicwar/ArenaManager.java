package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Random;

/** Owns the round: the countdown, the throwaway arena world, and who is standing where. */
public class ArenaManager {

    private final MagicWarPlugin plugin;
    private final String lobbyWorldName;
    private final String arenaPrefix;
    private final int countdownSeconds;
    private final int borderHalfWidth;
    private final Random random = new Random();

    private BukkitTask countdownTask;
    /** The arena for the current round, or null between rounds. */
    private World arena;

    public ArenaManager(MagicWarPlugin plugin, String lobbyWorldName, String arenaPrefix,
                        int countdownSeconds, int borderHalfWidth) {
        this.plugin = plugin;
        this.lobbyWorldName = lobbyWorldName;
        this.arenaPrefix = arenaPrefix;
        this.countdownSeconds = countdownSeconds;
        this.borderHalfWidth = borderHalfWidth;
    }

    public String lobbyWorldName() {
        return lobbyWorldName;
    }

    public World lobby() {
        return Bukkit.getWorld(lobbyWorldName);
    }

    public boolean isLobby(World world) {
        return world != null && world.getName().equals(lobbyWorldName);
    }

    public boolean isRunning() {
        return arena != null || countdownTask != null;
    }

    // ---------- starting a round ----------

    /** @return an error message, or null when the countdown started. */
    public String start() {
        if (isRunning()) {
            return "이미 라운드가 진행 중입니다. 먼저 /마법전쟁 중지 를 사용하세요.";
        }
        if (lobby() == null) {
            return "로비 월드 '" + lobbyWorldName + "' 를 찾을 수 없습니다. config.yml 의 lobby-world 를 확인하세요.";
        }
        announce(Component.text(countdownSeconds + "초 후에 시작됩니다...", NamedTextColor.YELLOW));

        // Generating a world blocks the main thread, so it goes one tick later: the countdown
        // message reaches everyone first, and the freeze lands at the start of the countdown
        // rather than at the end, where it would delay the teleport.
        Bukkit.getScheduler().runTask(plugin, this::createArena);

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = countdownSeconds;

            @Override
            public void run() {
                left--;
                if (left > 0) {
                    if (left <= 5) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.showTitle(Title.title(
                                    Component.text(left, NamedTextColor.GOLD), Component.empty(),
                                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        }
                    }
                    return;
                }
                stopCountdown();
                begin();
            }
        }, 20L, 20L);
        return null;
    }

    /** Fresh vanilla overworld, random seed, walled in at ±borderHalfWidth. */
    private void createArena() {
        String name = arenaPrefix + System.currentTimeMillis() / 1000;
        World world = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(random.nextLong())
                .createWorld();
        if (world == null) {
            plugin.getLogger().warning("Arena world creation returned null: " + name);
            return;
        }
        world.getWorldBorder().setCenter(0.5, 0.5);
        world.getWorldBorder().setSize(borderHalfWidth * 2.0);
        world.setKeepSpawnInMemory(false);
        arena = world;
    }

    private void begin() {
        if (arena == null) {
            // createArena failed, or the world is still being generated on a very slow disk.
            announce(Component.text("아레나 생성에 실패했습니다. 다시 시도해주세요.", NamedTextColor.RED));
            endRound();
            return;
        }
        Location spawn = safeArenaSpawn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.showTitle(Title.title(
                    Component.text("마법전쟁 시작!", NamedTextColor.RED), Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
    }

    /** The generated spawn can be underwater or in a tree; this drops everyone on the surface
     * at the middle of the arena instead. */
    private Location safeArenaSpawn() {
        int y = arena.getHighestBlockYAt(0, 0) + 1;
        return new Location(arena, 0.5, y, 0.5);
    }

    // ---------- ending a round ----------

    /** @return an error message, or null when the round was stopped. */
    public String stop() {
        if (!isRunning()) {
            return "진행 중인 라운드가 없습니다.";
        }
        stopCountdown();
        endRound();
        announce(Component.text("마법전쟁이 종료되었습니다. 로비로 돌아갑니다.", NamedTextColor.YELLOW));
        return null;
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    /** Everyone back to the lobby, then the arena is unloaded and deleted. */
    private void endRound() {
        World lobby = lobby();
        if (lobby != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isLobby(player.getWorld())) {
                    sendToLobby(player);
                }
            }
        }
        if (arena == null) {
            return;
        }
        World finished = arena;
        arena = null;
        File folder = finished.getWorldFolder();
        if (!Bukkit.unloadWorld(finished, false)) {
            plugin.getLogger().warning("Could not unload " + finished.getName() + "; it stays on disk until restart.");
            return;
        }
        deleteRecursively(folder.toPath());
    }

    /** Lobby spawn, adventure mode - the one place this is applied besides LobbyListener. */
    public void sendToLobby(Player player) {
        World lobby = lobby();
        if (lobby == null) {
            return;
        }
        player.teleport(lobby.getSpawnLocation());
        applyLobbyMode(player);
    }

    /** Adventure keeps the lobby build intact. Creative/spectator are left alone so an admin
     * can still edit the lobby without the plugin fighting them. */
    public void applyLobbyMode(Player player) {
        if (player.getGameMode() == GameMode.SURVIVAL) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    // ---------- housekeeping ----------

    public void shutdown() {
        stopCountdown();
        endRound();
    }

    /** Wipes arena folders left behind by a crash or a /stop in the middle of a round. */
    public void deleteLeftoverArenas() {
        File container = Bukkit.getWorldContainer();
        File[] entries = container.listFiles(
                (dir, name) -> name.startsWith(arenaPrefix) && new File(dir, name).isDirectory());
        if (entries == null) {
            return;
        }
        for (File folder : entries) {
            if (Bukkit.getWorld(folder.getName()) != null) {
                continue; // loaded somehow - leave it be
            }
            if (deleteRecursively(folder.toPath())) {
                plugin.getLogger().info("Removed leftover arena " + folder.getName());
            }
        }
    }

    private boolean deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not delete " + path + ": " + e.getMessage());
                }
            });
            return !Files.exists(root);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete " + root + ": " + e.getMessage());
            return false;
        }
    }

    private void announce(Component message) {
        Bukkit.getServer().sendMessage(message);
    }
}
