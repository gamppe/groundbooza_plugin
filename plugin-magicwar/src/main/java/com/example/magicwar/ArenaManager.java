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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Owns the round: countdown, the throwaway arena world, the opening grace period and the
 * battle-royale border.
 *
 * <p>The shrinking ring is plain vanilla: {@code WorldBorder#setSize(size, seconds)} animates
 * the wall inwards on its own and the client draws it, and the server applies the out-of-bounds
 * damage. Nothing here polls player positions.
 */
public class ArenaManager {

    private enum Phase { IDLE, COUNTDOWN, GRACE, FIGHT }

    private final MagicWarPlugin plugin;
    private final RoundSettings settings;
    private final Random random = new Random();
    /** Every scheduled step of the current round, so 중지 can drop all of them at once. */
    private final List<BukkitTask> roundTasks = new ArrayList<>();

    private Phase phase = Phase.IDLE;
    private World arena;

    public ArenaManager(MagicWarPlugin plugin, RoundSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public String lobbyWorldName() {
        return settings.lobbyWorld;
    }

    public World lobby() {
        return Bukkit.getWorld(settings.lobbyWorld);
    }

    public boolean isLobby(World world) {
        return world != null && world.getName().equals(settings.lobbyWorld);
    }

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    /** True while nobody can be hurt yet - the opening 5 minutes. */
    public boolean inGracePeriod() {
        return phase == Phase.GRACE;
    }

    // ---------- starting a round ----------

    /** @return an error message, or null when the countdown started. */
    public String start() {
        if (isRunning()) {
            return "이미 라운드가 진행 중입니다. 먼저 /마법전쟁 중지 를 사용하세요.";
        }
        if (lobby() == null) {
            return "로비 월드 '" + settings.lobbyWorld + "' 를 찾을 수 없습니다. config.yml 의 lobby-world 를 확인하세요.";
        }
        phase = Phase.COUNTDOWN;
        announce(Component.text(settings.countdownSeconds + "초 후에 시작됩니다...", NamedTextColor.YELLOW));

        // Generating a world blocks the main thread, so it goes one tick later: the countdown
        // message reaches everyone first, and the freeze lands at the start of the countdown
        // rather than at the end, where it would delay the teleport.
        Bukkit.getScheduler().runTask(plugin, this::createArena);

        track(Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = settings.countdownSeconds;

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
                cancelTasks();
                beginGrace();
            }
        }, 20L, 20L));
        return null;
    }

    /** Fresh vanilla overworld, random seed, walled in at the configured full size.
     *
     * <p>With terrain.avoid-ocean on, a world whose border encloses too much ocean is thrown
     * away and rolled again. There is no way to ask vanilla for "land only" - terrain shape
     * comes from the overworld noise, and a custom BiomeProvider would only relabel the water,
     * not remove it - so rejecting seeds is the honest option. Each attempt generates a whole
     * world on the main thread, which is why max-attempts is small. */
    private void createArena() {
        World accepted = null;
        for (int attempt = 1; attempt <= settings.maxAttempts; attempt++) {
            World candidate = generateWorld();
            if (candidate == null) {
                continue;
            }
            if (!settings.avoidOcean) {
                accepted = candidate;
                break;
            }
            double ocean = oceanFraction(candidate);
            if (ocean <= settings.maxOceanFraction || attempt == settings.maxAttempts) {
                if (ocean > settings.maxOceanFraction) {
                    plugin.getLogger().info(String.format(
                            "Arena still %.0f%% ocean after %d attempts - going with it.", ocean * 100, attempt));
                }
                accepted = candidate;
                break;
            }
            plugin.getLogger().info(String.format(
                    "Arena attempt %d was %.0f%% ocean - rerolling.", attempt, ocean * 100));
            discard(candidate);
        }
        if (accepted == null) {
            plugin.getLogger().warning("Could not generate an arena world.");
            return;
        }
        var border = accepted.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(settings.fullSize());
        border.setDamageAmount(settings.damagePerBlock);
        border.setDamageBuffer(settings.damageBuffer);
        border.setWarningDistance(settings.warningDistance);
        accepted.setKeepSpawnInMemory(false);
        arena = accepted;
    }

    private World generateWorld() {
        String name = settings.arenaPrefix + System.currentTimeMillis() / 1000 + "_" + random.nextInt(1000);
        World world = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(random.nextLong())
                .createWorld();
        if (world == null) {
            plugin.getLogger().warning("Arena world creation returned null: " + name);
        }
        return world;
    }

    /** Share of sampled points inside the border that sit in an ocean biome. Every sample pulls
     * in a chunk, so the grid is deliberately coarse. */
    private double oceanFraction(World world) {
        int n = settings.samplesPerSide;
        int half = settings.borderHalfWidth;
        int step = (half * 2) / (n - 1);
        int ocean = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = -half + i * step;
                int z = -half + j * step;
                if (world.getBiome(x, world.getSeaLevel(), z).getKey().getKey().contains("ocean")) {
                    ocean++;
                }
            }
        }
        return (double) ocean / (n * n);
    }

    private void discard(World world) {
        File folder = world.getWorldFolder();
        if (Bukkit.unloadWorld(world, false)) {
            deleteRecursively(folder.toPath());
        }
    }

    // ---------- grace period ----------

    private void beginGrace() {
        if (arena == null) {
            // createArena failed, or the world is still being generated on a very slow disk.
            announce(Component.text("아레나 생성에 실패했습니다. 다시 시도해주세요.", NamedTextColor.RED));
            endRound();
            return;
        }
        phase = Phase.GRACE;
        Location spawn = safeArenaSpawn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(true);
            player.showTitle(Title.title(
                    Component.text("준비 시간", NamedTextColor.AQUA),
                    Component.text(minutes(settings.graceSeconds) + " 동안 무적입니다", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
            player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        }
        announce(Component.text("준비 시간 " + minutes(settings.graceSeconds)
                + ". 이 동안은 아무도 피해를 입지 않습니다.", NamedTextColor.AQUA));

        // Reminders on the way down, then the fight.
        for (int at : new int[]{60, 30, 10}) {
            if (settings.graceSeconds > at) {
                track(Bukkit.getScheduler().runTaskLater(plugin,
                        () -> announce(Component.text("전투 시작까지 " + minutes(at), NamedTextColor.YELLOW)),
                        (settings.graceSeconds - at) * 20L));
            }
        }
        track(Bukkit.getScheduler().runTaskLater(plugin, this::beginFight, settings.graceSeconds * 20L));
    }

    // ---------- fight + shrinking border ----------

    private void beginFight() {
        phase = Phase.FIGHT;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setInvulnerable(false);
            player.showTitle(Title.title(
                    Component.text("전투 시작!", NamedTextColor.RED), Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
        announce(Component.text("전투 시작! 이제부터 피해를 입습니다.", NamedTextColor.RED));
        scheduleShrinkStages();
    }

    /** Lays the whole shrink schedule out up front as one-shot tasks; each stage's delay is the
     * sum of everything before it, so a slow tick never lets two stages overlap. */
    private void scheduleShrinkStages() {
        long delaySeconds = 0;
        double sizeBefore = settings.fullSize();
        for (RoundSettings.ShrinkStage stage : settings.shrinkStages) {
            delaySeconds += stage.waitSeconds();
            double from = sizeBefore;
            double to = stage.toSize();
            int over = stage.overSeconds();
            track(Bukkit.getScheduler().runTaskLater(plugin, () -> startShrink(from, to, over), delaySeconds * 20L));
            delaySeconds += over;
            sizeBefore = to;
        }
    }

    private void startShrink(double from, double to, int overSeconds) {
        if (arena == null) {
            return;
        }
        arena.getWorldBorder().setSize(to, overSeconds);
        announce(Component.text("경계가 좁혀집니다: " + (int) from + " → " + (int) to
                + " (" + overSeconds + "초)", NamedTextColor.GOLD));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(arena)) {
                player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            }
        }
    }

    /** The generated spawn can be underwater or in a tree; this drops everyone on the surface
     * at the middle of the arena instead - all on the same spot, by design. */
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
        cancelTasks();
        endRound();
        announce(Component.text("마법전쟁이 종료되었습니다. 로비로 돌아갑니다.", NamedTextColor.YELLOW));
        return null;
    }

    private void track(BukkitTask task) {
        roundTasks.add(task);
    }

    private void cancelTasks() {
        roundTasks.forEach(BukkitTask::cancel);
        roundTasks.clear();
    }

    /** Everyone back to the lobby, then the arena is unloaded and deleted. */
    private void endRound() {
        phase = Phase.IDLE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setInvulnerable(false);
            if (!isLobby(player.getWorld())) {
                sendToLobby(player);
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
        player.setInvulnerable(false);
        if (player.getGameMode() == GameMode.SURVIVAL) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    // ---------- housekeeping ----------

    public void shutdown() {
        cancelTasks();
        endRound();
    }

    /** Wipes arena folders left behind by a crash or a /stop in the middle of a round. */
    public void deleteLeftoverArenas() {
        File container = Bukkit.getWorldContainer();
        File[] entries = container.listFiles(
                (dir, name) -> name.startsWith(settings.arenaPrefix) && new File(dir, name).isDirectory());
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

    private static String minutes(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + "분";
        }
        if (seconds > 60) {
            return (seconds / 60) + "분 " + (seconds % 60) + "초";
        }
        return seconds + "초";
    }

    private void announce(Component message) {
        Bukkit.getServer().sendMessage(message);
    }
}
