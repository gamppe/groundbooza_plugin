package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitRunnable;
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
 * <p>The shrinking ring is mostly plain vanilla: {@code WorldBorder#setSize(size, seconds)}
 * animates the wall inwards on its own and the client draws it, and the server applies the
 * out-of-bounds damage. Nothing here polls player positions. Only the centre needs help -
 * {@code setCenter} has no duration, so it is walked across the same ticks by hand.
 */
public class ArenaManager {

    private enum Phase { IDLE, COUNTDOWN, GRACE, FIGHT }

    /** Mid-morning: bright, with the sun off to one side rather than flat overhead. */
    private static final long LOBBY_TIME = 6000L;

    private final MagicWarPlugin plugin;
    private final RoundSettings settings;
    private final ClassManager classes;
    private final QuestManager quests;
    private final ClassGuideItem guide;
    private ClassController classController;
    private final Random random = new Random();
    /** Every scheduled step of the current round, so 중지 can drop all of them at once. */
    private final List<BukkitTask> roundTasks = new ArrayList<>();

    private Phase phase = Phase.IDLE;
    private World arena;
    /** The round's map item, handed out at the teleport; null when give-map is off. */
    private ItemStack arenaMap;
    /** The ring announced by the last warning, drawn on the map until the wall starts moving. */
    private boolean pendingZone;
    private double pendingCenterX;
    private double pendingCenterZ;
    private double pendingSize;

    public ArenaManager(MagicWarPlugin plugin, RoundSettings settings, ClassManager classes,
                        QuestManager quests, ClassGuideItem guide) {
        this.plugin = plugin;
        this.settings = settings;
        this.classes = classes;
        this.quests = quests;
        this.guide = guide;
    }

    /** The lobby is scenery, not a place to fight: no mobs, and the sun never moves. The
     * gamerule stops natural spawning; LobbyListener catches everything else (spawners, eggs,
     * plugin spawns). Called once the worlds are up. */
    public void applyLobbyRules() {
        World lobby = lobby();
        if (lobby == null) {
            plugin.getLogger().warning("Lobby world '" + settings.lobbyWorld + "' not found - rules not applied.");
            return;
        }
        lobby.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        lobby.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        lobby.setTime(LOBBY_TIME);
        lobby.setDifficulty(Difficulty.PEACEFUL);
    }

    /** Everyone still standing in the arena - what the sidebar counts. */
    public int alivePlayers() {
        if (arena == null) {
            return 0;
        }
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(arena) && p.getGameMode() == GameMode.SURVIVAL)
                .count();
    }

    /** Set after construction: the controller needs the manager, and the manager opens the
     * picker at the teleport, so one of the two links has to be late. */
    public void setClassController(ClassController classController) {
        this.classController = classController;
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

    /** Fresh vanilla overworld, random seed, walled in at the configured full size. */
    private void createArena() {
        String name = settings.arenaPrefix + System.currentTimeMillis() / 1000;
        World world = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(random.nextLong())
                .createWorld();
        if (world == null) {
            plugin.getLogger().warning("Arena world creation returned null: " + name);
            return;
        }
        world.setDifficulty(Difficulty.HARD);
        var border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(settings.fullSize());
        border.setDamageAmount(settings.damagePerBlock);
        border.setDamageBuffer(settings.damageBuffer);
        border.setWarningDistance(settings.warningDistance);
        world.setKeepSpawnInMemory(false);
        arena = world;
        arenaMap = settings.giveMap ? createArenaMap(world) : null;
    }

    /** One filled map per round, centred on the arena at the widest zoom, with the border
     * painted over the terrain by BorderMapRenderer. */
    private ItemStack createArenaMap(World world) {
        MapView view = Bukkit.createMap(world);
        view.setScale(MapView.Scale.FARTHEST);
        view.setCenterX(0);
        view.setCenterZ(0);
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(false);
        view.setLocked(false);
        view.addRenderer(new BorderMapRenderer(this));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text("아레나 지도", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("보라색 선이 현재 경계입니다", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
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
            // Everyone starts a round on the same footing: nothing carried over from the lobby
            // or from a previous match, only what giveKit hands out below.
            clearEverything(player);
            // Speed and Haste for exactly as long as the peace lasts, to spend the prep time
            // spreading out and gathering rather than walking.
            int graceTicks = settings.graceSeconds * 20;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, graceTicks, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, graceTicks, 1));
            giveKit(player);
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
     * sum of everything before it, so a slow tick never lets two stages overlap. Every stage is
     * two tasks: the warning, then the shrink itself once the escape window has passed. */
    private void scheduleShrinkStages() {
        long delaySeconds = 0;
        double sizeBefore = settings.fullSize();
        int number = 0;
        for (RoundSettings.ShrinkStage stage : settings.shrinkStages) {
            number++;
            delaySeconds += stage.waitSeconds();
            double from = sizeBefore;
            double to = stage.toSize();
            int warn = stage.warnSeconds();
            int over = stage.overSeconds();
            int label = number;
            track(Bukkit.getScheduler().runTaskLater(plugin,
                    () -> warnShrink(label, from, to, warn), delaySeconds * 20L));
            delaySeconds += warn;
            track(Bukkit.getScheduler().runTaskLater(plugin,
                    () -> startShrink(from, to, over), delaySeconds * 20L));
            delaySeconds += over;
            sizeBefore = to;
        }
    }

    /** One action bar line and a chime, nothing more - the map keeps showing the pending ring
     * for the rest of the window, so there is no need to keep repeating the text. The centre is
     * picked here rather than at the shrink: a warning is only worth giving if it says where to
     * run, and that ring is what the map draws until the wall actually starts moving. */
    private void warnShrink(int number, double from, double to, int warnSeconds) {
        if (arena == null) {
            return;
        }
        Location current = arena.getWorldBorder().getCenter();
        if (settings.randomCenter) {
            double[] target = pickCenter(current.getX(), current.getZ(), from / 2, to / 2);
            pendingCenterX = target[0];
            pendingCenterZ = target[1];
        } else {
            pendingCenterX = current.getX();
            pendingCenterZ = current.getZ();
        }
        pendingSize = to;
        pendingZone = true;

        Component line = Component.text("[경고] ", NamedTextColor.RED)
                .append(Component.text(number + "차 자기장이 " + minutes(warnSeconds) + " 뒤 축소", NamedTextColor.YELLOW))
                .append(Component.text("  ·  중심 (" + (int) pendingCenterX + ", " + (int) pendingCenterZ
                        + ")  ·  크기 " + (int) to, NamedTextColor.GRAY));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(arena)) {
                player.sendActionBar(line);
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
            }
        }
    }

    private void startShrink(double from, double to, int overSeconds) {
        if (arena == null) {
            return;
        }
        WorldBorder border = arena.getWorldBorder();
        border.setSize(to, overSeconds);

        Component where = Component.empty();
        if (pendingZone) {
            Location current = border.getCenter();
            slideCenter(border, current.getX(), current.getZ(), pendingCenterX, pendingCenterZ, overSeconds);
            where = Component.text(" · 중심 (" + (int) pendingCenterX + ", " + (int) pendingCenterZ + ")",
                    NamedTextColor.YELLOW);
            pendingZone = false; // the wall is on its way there now; stop drawing it as pending
        }
        announce(Component.text("경계가 좁혀집니다: " + (int) from + " → " + (int) to
                + " (" + overSeconds + "초)", NamedTextColor.GOLD).append(where));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(arena)) {
                player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            }
        }
    }

    /** A new centre somewhere inside the current ring, close enough in that the smaller circle
     * still fits entirely within the old one: offset at most (oldRadius - newRadius). Without
     * that cap someone standing safely inside could end up outside without moving. The distance
     * is sqrt-weighted so the point is spread evenly over the disc instead of bunching up in
     * the middle. */
    private double[] pickCenter(double x, double z, double fromRadius, double toRadius) {
        double maxOffset = Math.max(0, fromRadius - toRadius);
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = maxOffset * Math.sqrt(random.nextDouble());
        return new double[]{x + Math.cos(angle) * distance, z + Math.sin(angle) * distance};
    }

    /** setSize animates itself, but setCenter teleports the wall, so the centre is walked over
     * the same number of ticks by hand. */
    private void slideCenter(WorldBorder border, double fromX, double fromZ, double toX, double toZ, int overSeconds) {
        if (overSeconds <= 0) {
            border.setCenter(toX, toZ);
            return;
        }
        int totalTicks = overSeconds * 20;
        track(new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed++;
                double t = Math.min(1.0, (double) elapsed / totalTicks);
                border.setCenter(fromX + (toX - fromX) * t, fromZ + (toZ - fromZ) * t);
                if (t >= 1.0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L));
    }

    /** Wipes a player back to nothing. PlayerInventory.clear() already covers armour and the
     * off hand, but a cursor item survives it - that is what an open GUI at the moment of the
     * teleport would leave behind. */
    private void clearEverything(Player player) {
        player.getInventory().clear();
        player.setItemOnCursor(null);
    }

    /** Starting gear, plus the class picker for anyone who has not chosen yet. The picker is
     * opened a tick later so it lands after the teleport rather than being closed by it. */
    private void giveKit(Player player) {
        if (settings.giveMap && arenaMap != null) {
            player.getInventory().addItem(arenaMap.clone());
        }
        player.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
        player.getInventory().addItem(new ItemStack(Material.BREAD, 10));
        player.getInventory().addItem(guide.create(classes.displayName(player.getUniqueId())));

        if (classController != null && !classes.hasClass(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    classController.openSelect(player);
                }
            });
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
        classes.clear(); // a class lasts one match
        quests.clear();
        plugin.getSkillCooldowns().clear();
        plugin.getSkillEffects().clear();
        plugin.getFrostState().clear();
        plugin.getTempBlocks().clear();
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
        arenaMap = null;
        pendingZone = false;
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

    /** Where the next ring will be, or null when no warning is outstanding.
     * {@code [centreX, centreZ, size]}. */
    public double[] pendingZone() {
        return pendingZone ? new double[]{pendingCenterX, pendingCenterZ, pendingSize} : null;
    }

    private static String describe(int seconds) {
        return minutes(seconds);
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
