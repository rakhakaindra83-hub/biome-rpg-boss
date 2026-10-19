/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  net.kyori.adventure.key.Key
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.SoundCategory
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerResourcePackStatusEvent
 *  org.bukkit.event.player.PlayerResourcePackStatusEvent$Status
 *  org.bukkit.plugin.IllegalPluginAccessException
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.backsound.youtube;

import com.backsound.youtube.CassetteIcon;
import com.backsound.youtube.LocalLibrary;
import com.backsound.youtube.MediaExtractor;
import com.backsound.youtube.PackBuilder;
import com.backsound.youtube.PackServer;
import com.backsound.youtube.Settings;
import com.backsound.youtube.YouTubeUrl;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class YouTubeBacksound
extends JavaPlugin
implements Listener,
CommandExecutor,
TabCompleter {
    private volatile boolean closed;
    private final BossCombatTracker encounters = new BossCombatTracker();
    private volatile BossMusicSettings bossSettings = BossMusicSettings.empty();
    private MythicBossHook bossHook;
    private volatile RpgBossController rpgBoss;
    int caveDepth() { return this.biomeConfig.caveMaxY(); }
    boolean hasBossProfile(String type) { return this.bossSettings.profiles().containsKey(type); }

    private void installBossFiles() throws Exception {
        Path data = this.getDataFolder().toPath();
        if (!Files.exists(data.resolve("boss-music.yml"))) this.saveResource("boss-music.yml", false);
        for (String path : List.of("Mobs/BiomeBosses.yml", "Skills/BiomeBossSkills.yml", "packinfo.yml")) {
            Path target = data.resolve("boss-pack").resolve(path);
            if (Files.exists(target)) continue;
            Files.createDirectories(target.getParent());
            try (var input = this.getResource("boss-pack/" + path)) {
                if (input == null) throw new java.io.IOException("Resource boss hilang: " + path);
                Files.copy(input, target);
            }
        }
    }

    private void installRpgResources() throws Exception {
        Path data = this.getDataFolder().toPath();
        for (String name : List.of("boss-spawns.yml", "rpgboss-resourcepack.zip", "rpgboss-datapack.zip", "PETUNJUK-RPG-AUTO.txt")) {
            Path target = data.resolve(name);
            if (Files.exists(target)) continue;
            try (var input = this.getResource(name)) {
                if (input == null) throw new java.io.IOException("Resource RPG hilang: " + name);
                Files.copy(input, target);
            }
        }
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(data.resolve("boss-spawns.yml").toFile());
        try {
        if (config.getBoolean("install-datapack", true) && !this.getServer().getWorlds().isEmpty()) {
            Path directory = this.getServer().getWorlds().get(0).getWorldFolder().toPath().resolve("datapacks");
            Files.createDirectories(directory);
            Path target = directory.resolve("rpgboss-data.zip");
            if (!Files.exists(target)) {
                Files.copy(data.resolve("rpgboss-datapack.zip"), target);
                this.getLogger().info("Datapack loot RPG disalin otomatis. Aktif pada restart berikutnya; loot fallback plugin langsung tersedia.");
            }
        }
        } catch (java.io.IOException ex) {
            this.getLogger().warning("Datapack loot belum tersalin ke world: " + ex.getMessage() + ". Plugin memakai loot fallback; file tersedia di folder plugin.");
        }
    }

    private void enableBossHook() {
        if (this.closed || this.bossHook != null) return;
        Plugin mythic = this.getServer().getPluginManager().getPlugin("MythicMobs");
        if (mythic == null || !mythic.isEnabled()) return;
        try {
            int copied = 0;
            for (String path : List.of("Mobs/BiomeBosses.yml", "Skills/BiomeBossSkills.yml", "packinfo.yml")) {
                Path target = mythic.getDataFolder().toPath().resolve("Packs/RPG_Biome_Bosses").resolve(path);
                if (Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                Files.copy(this.getDataFolder().toPath().resolve("boss-pack").resolve(path), target);
                copied++;
            }
            this.bossHook = new MythicBossHook(this, this.encounters);
            this.bossHook.start();
            RpgBossController controller = new RpgBossController(this);
            try { controller.start(); this.rpgBoss = controller; }
            catch (Exception | LinkageError ex) { controller.close(); throw ex; }
            this.getLogger().info("Integrasi MythicMobs aktif. Musik boss menunggu audio/aktivasi di boss-music.yml.");
            if (copied > 0) {
                this.getLogger().info("Enam boss RPG dipasang. MythicMobs akan dimuat ulang sekali untuk mendaftarkan pack baru.");
                this.getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
                    if (!this.closed && mythic.isEnabled()) this.getServer().dispatchCommand(this.getServer().getConsoleSender(), "mythicmobs reload");
                }, 20L);
            }
        } catch (Exception | LinkageError ex) {
            if (this.rpgBoss != null) { this.rpgBoss.close(); this.rpgBoss = null; }
            if (this.bossHook != null) this.bossHook.close();
            this.bossHook = null;
            this.getLogger().warning("Integrasi boss belum aktif; musik biome tetap berjalan: " + ex.getMessage());
        }
    }

    @EventHandler
    public void pluginEnabled(org.bukkit.event.server.PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("MythicMobs")) this.enableBossHook();
    }
    @EventHandler
    public void pluginDisabled(org.bukkit.event.server.PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("MythicMobs") && this.bossHook != null) {
            this.bossHook.close(); this.bossHook = null;
            if (this.rpgBoss != null) { this.rpgBoss.close(); this.rpgBoss = null; }
        }
    }

    private Session selectPlayback(Player player, Viewer state) {
        Session biome = this.selectSession(player, state);
        state.playbackLabel = state.biomeName;
        BossMusicSettings config = this.bossSettings;
        LocalLibrary prepared = this.library;
        BossCombatTracker.Boss boss = null;
        if (config.enabled() && prepared != null && !player.isDead()) {
            var location = player.getLocation();
            boss = this.encounters.select(player.getUniqueId(), new BossCombatTracker.Position(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ()), System.nanoTime(), config.timeout(), config.range(), type -> {
                var profile = config.profiles().get(type);
                return profile != null && profile.enabled() && profile.files().stream().allMatch(prepared.tracks()::containsKey);
            });
        }
        if (boss == null) {
            state.bossSession = null; state.bossId = null; state.bossLibrary = null; state.bossProfile = null;
            return biome;
        }
        var profile = config.profiles().get(boss.type());
        if (state.bossSession == null || !boss.id().equals(state.bossId) || state.bossLibrary != prepared || state.bossProfile != profile) {
            if (!boss.id().equals(state.bossId) || state.bossLibrary != prepared || state.bossProfile != profile) state.bossTrackIndex = 0;
            state.bossTrackIndex = BiomePolicy.playlistIndex(state.bossTrackIndex, profile.files().size());
            var track = prepared.find(new Settings.Source(profile.files().get(state.bossTrackIndex), profile.name(), true));
            if (track == null) return biome;
            state.bossSession = new Session(track, this.settings, System.nanoTime(), Set.of(player.getUniqueId()), state.bossTrackIndex + 1, profile.files().size());
            state.bossId = boss.id(); state.bossLibrary = prepared; state.bossProfile = profile;
        }
        state.playbackLabel = "BOSS: " + profile.name();
        return state.bossSession;
    }

    private record BiomeConfig(boolean enabled, boolean titles, int caveMaxY, java.util.Map<String, BiomePolicy.Profile> profiles, java.util.Map<String, String> overrides, java.util.Map<String, List<String>> playlists, long fadeOutNanos, long fadeInNanos) {}
    private volatile BiomeConfig biomeConfig = new BiomeConfig(true, true, 48, BiomePolicy.PROFILES, java.util.Map.of(), java.util.Map.of(), TimeUnit.SECONDS.toNanos(2), TimeUnit.MILLISECONDS.toNanos(1500));
    private org.bukkit.NamespacedKey volumeKey;
    private final SpatialSoundBridge spatialSound = new SpatialSoundBridge(message -> this.getLogger().warning(message));

    private BiomeConfig readBiomeConfig() {
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(this.getDataFolder(), "biome-music.yml"));
        boolean enabled = yaml.getBoolean("enabled", true);
        boolean titles = yaml.getBoolean("show-biome-titles", true);
        int caveMaxY = Math.max(-64, Math.min(320, yaml.getInt("cave-max-y", 48)));
        var profiles = new java.util.HashMap<String, BiomePolicy.Profile>();
        var playlists = new java.util.HashMap<String, List<String>>();
        for (var entry : BiomePolicy.PROFILES.entrySet()) {
            var profile = entry.getValue();
            String prefix = "profiles." + entry.getKey() + ".";
            String file = yaml.getString(prefix + "file", profile.file());
            String fallback = yaml.getString(prefix + "fallback", profile.fallback());
            Settings.validateFile(file);
            if (!file.toLowerCase(Locale.ROOT).endsWith(".ogg")) throw new IllegalArgumentException("Musik biome harus OGG Vorbis");
            if (!fallback.isEmpty()) Settings.validateFile(fallback);
            List<String> files = yaml.getStringList(prefix + "files");
            if (files.isEmpty()) files = List.of(file);
            if (files.size() > 100) throw new IllegalArgumentException("Maksimal 100 lagu per biome");
            for (String item : files) {
                Settings.validateFile(item);
                if (!item.toLowerCase(Locale.ROOT).endsWith(".ogg")) throw new IllegalArgumentException("Playlist biome harus OGG");
            }
            playlists.put(entry.getKey(), List.copyOf(files));
            String color = yaml.getString(prefix + "color", String.format("#%06X", profile.color()));
            int rgb = Integer.parseInt(color.replace("#", ""), 16);
            profiles.put(entry.getKey(), new BiomePolicy.Profile(profile.group(), profile.name(), rgb, file, fallback, yaml.getString(prefix + "title", profile.title())));
        }

        var overrides = new java.util.HashMap<String, String>();
        var section = yaml.getConfigurationSection("overrides");
        if (section != null) for (String biome : section.getKeys(false)) {
            String group = section.getString(biome);
            if (!profiles.containsKey(group)) throw new IllegalArgumentException("Profil biome tidak dikenal: " + group);
            overrides.put(biome.toLowerCase(Locale.ROOT), group);
        }
        return new BiomeConfig(enabled, titles, caveMaxY, java.util.Map.copyOf(profiles), java.util.Map.copyOf(overrides), java.util.Map.copyOf(playlists), fadeDuration(yaml.getDouble("fade-out-seconds", 2)), fadeDuration(yaml.getDouble("fade-in-seconds", 1.5)));
    }

    private static long fadeDuration(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 10) throw new IllegalArgumentException("Durasi fade harus 0 sampai 10 detik");
        return (long)(seconds * 1_000_000_000L);
    }

    private Session selectSession(Player player, Viewer state) {
        if (state.volume < 0) {
            Integer saved = player.getPersistentDataContainer().get(this.volumeKey, org.bukkit.persistence.PersistentDataType.INTEGER);
            state.volume = BiomePolicy.clampVolume(saved == null ? 70 : saved);
            if (state.volume > 0) state.lastVolume = state.volume;
        }
        BiomeConfig config = this.biomeConfig;
        var location = player.getLocation();
        var block = location.getBlock();
        String biome = block.getBiome().getKey().getKey();
        String group = BiomePolicy.group(biome, player.getWorld().getEnvironment().name(), location.getBlockY(), 0, false, config.caveMaxY());
        // Underground profiles take priority over surface biome overrides.
        if (!Set.of("cave", "deep-dark", "lush-caves").contains(group)) group = config.overrides().getOrDefault(biome, group);
        BiomePolicy.Profile profile = config.profiles().get(group);
        String display = group.equals("cave") && !biome.equals("dripstone_caves") ? "Cave" : BiomePolicy.displayName(biome);
        String displayKey = player.getWorld().getUID() + ":" + biome + ":" + group;
        if (!displayKey.equals(state.displayKey)) {
            state.displayKey = displayKey;
            state.pendingTitleAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(800);
            state.titlePending = true;
        }
        state.biomeName = display;
        state.biomeColor = profile.color();
        if (state.titlePending && System.nanoTime() >= state.pendingTitleAt) {
            state.titlePending = false;
            if (config.titles()) player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text(display, TextColor.color(profile.color())),
                Component.text("♪ " + profile.title(), NamedTextColor.GRAY),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(600))));
        }
        LocalLibrary prepared = this.library;
        if (!config.enabled() || prepared == null) {
            state.biomeSession = null;
            state.biomeLibrary = null;
            state.biomeGroup = null;
            return this.current;
        }
        if (state.biomeSession == null || state.biomeLibrary != prepared || !group.equals(state.biomeGroup) || state.biomeProfile != profile) {
            if (!group.equals(state.biomeGroup) || state.biomeLibrary != prepared || state.biomeProfile != profile) state.biomeTrackIndex = 0;
            List<String> playlist = config.playlists().getOrDefault(group, List.of(profile.file()));
            state.biomeTrackIndex = BiomePolicy.playlistIndex(state.biomeTrackIndex, playlist.size());
            String chosen = playlist.get(state.biomeTrackIndex);
            String songTitle = profile.title() + (playlist.size() > 1 ? " [" + (state.biomeTrackIndex + 1) + "/" + playlist.size() + "]" : "");
            MediaExtractor.Track track = prepared.find(new Settings.Source(chosen, songTitle, true));
            if (track == null && !profile.fallback().isBlank()) track = prepared.find(new Settings.Source(profile.fallback(), (group.equals("snow") ? "Snowfall" : profile.title()) + " (original)", true));
            if (track == null) {
                state.biomeSession = null;
                state.biomeLibrary = null;
                state.biomeGroup = null;
                return this.current;
            }
            // Keep metadata/title for an uploaded Snowy, but label the bundled fallback honestly.
            state.biomeSession = new Session(track, this.settings, System.nanoTime(), Set.of(player.getUniqueId()), 1, 1);
            state.biomeLibrary = prepared;
            state.biomeGroup = group;
            state.biomeProfile = profile;
        }
        return state.biomeSession;
    }

    private static final class VolumeMenu implements org.bukkit.inventory.InventoryHolder {
        final UUID owner;
        org.bukkit.inventory.Inventory inventory;
        VolumeMenu(UUID owner) { this.owner = owner; }
        public org.bukkit.inventory.Inventory getInventory() { return this.inventory; }
    }

    private org.bukkit.inventory.ItemStack menuItem(org.bukkit.Material material, String name, String... lore) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(Arrays.stream(lore).map(line -> (Component)Component.text(line, NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void drawVolume(VolumeMenu menu, Viewer state) {
        var inventory = menu.inventory;
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, menuItem(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, " "));
        inventory.setItem(10, menuItem(org.bukkit.Material.RED_DYE, "Volume -10%", "Kecilkan musik hanya untukmu"));
        inventory.setItem(12, menuItem(org.bukkit.Material.NOTE_BLOCK, "Volume: " + state.volume + "%", "Biome: " + state.biomeName, "Perubahan volume mengulang lagu dari awal", "Volume Jukebox di pengaturan game tetap berpengaruh"));
        inventory.setItem(14, menuItem(org.bukkit.Material.LIME_DYE, "Volume +10%", "Besarkan musik hanya untukmu"));
        inventory.setItem(16, menuItem(state.volume == 0 ? org.bukkit.Material.LIME_WOOL : org.bukkit.Material.RED_WOOL, state.volume == 0 ? "Nyalakan musik" : "Mute", "Klik untuk mengubah"));
        inventory.setItem(22, menuItem(org.bukkit.Material.BARRIER, "Tutup"));
    }

    private void openVolume(Player player) {
        Viewer state = this.viewers.get(player.getUniqueId());
        if (state == null) return;
        this.selectSession(player, state);
        VolumeMenu menu = new VolumeMenu(player.getUniqueId());
        menu.inventory = this.getServer().createInventory(menu, 27, Component.text("♪ Volume Musik", NamedTextColor.DARK_AQUA));
        this.drawVolume(menu, state);
        player.openInventory(menu.inventory);
    }

    private void changeVolume(Player player, Viewer state, int volume) {
        int clamped = BiomePolicy.clampVolume(volume);
        if (clamped == state.volume) return;
        state.volume = clamped;
        if (clamped > 0) state.lastVolume = clamped;
        player.getPersistentDataContainer().set(this.volumeKey, org.bukkit.persistence.PersistentDataType.INTEGER, clamped);
        Session session = state.seen;
        if (session != null && state.phase == Phase.PLAYING) {
            player.stopSound(session.track().sound(), SoundCategory.RECORDS);
            this.startSound(player, state, session);
        }
    }

    @EventHandler
    public void volumeClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof VolumeMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (!Set.of(10, 14, 16, 22).contains(slot)) return;
        player.getScheduler().run(this, task -> {
            if (this.closed || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            Viewer state = this.viewers.get(player.getUniqueId());
            if (state == null) return;
            if (slot == 22) { player.closeInventory(); return; }
            if (slot == 10) this.changeVolume(player, state, state.volume - 10);
            if (slot == 14) this.changeVolume(player, state, state.volume + 10);
            if (slot == 16) this.changeVolume(player, state, state.volume == 0 ? state.lastVolume : 0);
            this.drawVolume(menu, state);
        }, null);
    }

    @EventHandler
    public void volumeDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof VolumeMenu) event.setCancelled(true);
    }

    private volatile Settings settings;
    private volatile Session current;
    private volatile LocalLibrary library;
    private MediaExtractor extractor;
    private PackServer http;
    private ThreadPoolExecutor worker;
    private Future<?> job;
    private ScheduledTask monitor;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final AtomicBoolean checking = new AtomicBoolean();
    private final ConcurrentMap<UUID, Viewer> viewers = new ConcurrentHashMap<UUID, Viewer>();
    private final Deque<Settings.Source> queue = new ArrayDeque<Settings.Source>();
    private long generation;
    private boolean loading;
    private int index;
    private int total;

    public void onEnable() {
        this.saveDefaultConfig();
        this.volumeKey = new org.bukkit.NamespacedKey(this, "music_volume");
        if (!this.getDataFolder().toPath().resolve("playlist.txt").toFile().exists()) {
            this.saveResource("playlist.txt", false);
        }
        try {
            this.installBiomeConfiguration();
            this.installBossFiles();
            this.installRpgResources();
            this.bossSettings = BossMusicSettings.read(this.getDataFolder().toPath().resolve("boss-music.yml"));
            this.reloadConfig();
            if (this.getConfig().getInt("pack-load-timeout-seconds", 45) < 120) {
                this.getConfig().set("pack-load-timeout-seconds", 120);
                this.saveConfig();
            }
            this.biomeConfig = this.readBiomeConfig();
            this.settings = Settings.read(this.getDataFolder().toPath().resolve("config.yml"));
            this.extractor = new MediaExtractor(this.getDataFolder().toPath());
            this.http = new PackServer(this.settings);
            this.worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), r -> {
                Thread thread = new Thread(r, "YouTubeBacksound-media");
                thread.setDaemon(true);
                return thread;
            });
            this.worker.submit(() -> {
                block3: {
                    try {
                        this.installBundledMusic();
                        LocalLibrary prepared = LocalLibrary.build(this.getDataFolder().toPath(), this.settings);
                        if (this.closed) {
                            return;
                        }
                        Files.write(this.getDataFolder().toPath().resolve("resourcepack.zip"), prepared.pack().zip(), new OpenOption[0]);
                        this.global(() -> {
                            this.library = prepared;
                            this.http.library = prepared.pack();
                            this.getLogger().info("Resource pack gabungan siap: " + prepared.tracks().size() + " lagu dan ikon kaset.");
                        });
                    }
                    catch (Exception ex) {
                        if (this.closed) break block3;
                        this.getLogger().warning("Resource pack saat join belum siap: " + ex.getMessage());
                    }
                }
            });
            PluginCommand command = Objects.requireNonNull(this.getCommand("ytmusic"));
            command.setExecutor((CommandExecutor)this);
            command.setTabCompleter((TabCompleter)this);
            Objects.requireNonNull(this.getCommand("musicgui")).setExecutor(this);
            Objects.requireNonNull(this.getCommand("rpgboss")).setExecutor(this);
            Objects.requireNonNull(this.getCommand("rpgboss")).setTabCompleter(this);
            this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
            this.enableBossHook();
            for (Player player : this.getServer().getOnlinePlayers()) {
                this.attach(player);
            }
            this.monitor = this.getServer().getGlobalRegionScheduler().runAtFixedRate((Plugin)this, task -> this.tick(), 1L, 1L);
            if (this.settings.publicUrl().contains("IP-SERVER")) {
                this.getLogger().warning("Atur public-base-url pada config.yml, lalu /ytmusic reload sebelum play.");
            }
        }
        catch (Exception ex) {
            this.getLogger().severe("Plugin gagal aktif: " + ex.getMessage());
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        this.attach(event.getPlayer());
    }

    @EventHandler
    public void respawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().run(this, task -> {
            Viewer state = this.viewers.get(player.getUniqueId());
            if (state == null || this.closed) return;
            this.cleanup(player, state);
            SpatialSoundBridge.Channel channel = state.channel;
            state.channel = null;
            if (channel != null) channel.close();
        }, null);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        this.encounters.clearPlayer(event.getPlayer().getUniqueId());
        Viewer state = (Viewer)this.viewers.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            this.retire(state);
        }
    }

    private void retire(Viewer state) {
        state.live = false;
        SpatialSoundBridge.Channel channel = state.channel;
        state.channel = null;
        if (channel != null) channel.close();
        if (state.task != null) {
            state.task.cancel();
        }
    }

    private void attach(Player player) {
        Viewer state;
        if (this.closed) {
            return;
        }
        UUID id = player.getUniqueId();
        Viewer old = this.viewers.put(id, state = new Viewer());
        if (old != null) {
            this.retire(old);
        }
        state.task = player.getScheduler().runAtFixedRate((Plugin)this, task -> {
            if (this.closed || !state.live) {
                task.cancel();
                return;
            }
            this.render(player, state);
        }, () -> {
            this.retire(state);
            this.viewers.remove(id, state);
        }, 1L, 1L);
        if (state.task == null || !state.live || this.closed) {
            this.retire(state);
            this.viewers.remove(id, state);
        }
    }

    private void cleanup(Player player, Viewer state) {
        if (state.seen != null) {
            player.stopSound(state.seen.track().sound(), SoundCategory.RECORDS);
            if (!state.seen.track().pack().id().equals(state.baseId)) {
                player.removeResourcePack(state.seen.track().pack().id());
            }
            player.sendActionBar((Component)Component.empty());
        }
        state.seen = null;
        state.fadingOut = false;
        state.gain.set(1);
        state.phase = Phase.IDLE;
        state.frame = -1L;
    }

    private void render(Player player, Viewer state) {
        this.offerBase(player, state);
        Session session = this.selectPlayback(player, state);
        long now = System.nanoTime();
        SpatialSoundBridge.Channel previousChannel = state.channel;
        if (previousChannel != null && !previousChannel.world.equals(player.getWorld().getUID())) {
            this.cleanup(player, state);
            previousChannel.close(); state.channel = null;
        }
        // Returning across a biome border during fade-out keeps the old sound
        // playing and reverses its envelope instead of restarting its audio.
        if (session != null && state.seen != null && session != state.seen
            && state.phase == Phase.PLAYING
            && session.track().sound().equals(state.seen.track().sound())
            && session.track().pack().id().equals(state.seen.track().pack().id())) {
            if (session == state.biomeSession) state.biomeSession = state.seen;
            if (session == state.bossSession) state.bossSession = state.seen;
            session = state.seen;
        }
        if (state.seen != session) {
            if (state.seen != null && session != null && state.phase == Phase.PLAYING
                && state.channel != null && state.volume > 0 && now < state.end) {
                if (!state.fadingOut) {
                    state.fadingOut = true;
                    state.gain.to(0, this.biomeConfig.fadeOutNanos(), now);
                }
                this.positionSound(player, state, now);
                if (state.channel != null && !state.gain.done(now)) return;
            }

            this.cleanup(player, state);
            if (session != null) {
                state.seen = session;
                state.phase = Phase.WAITING;
                state.waitingSince = System.nanoTime();
                PackBuilder.Pack pack = session.track().pack();
                if (!pack.id().equals(state.baseId)) {
                    player.addResourcePack(pack.id(), session.settings().publicUrl() + "/packs/" + String.valueOf(pack.id()) + ".zip", pack.sha1(), "Muat backsound: " + session.track().title(), false);
                }
            }
        }
        if (session == null || state.seen != session) {
            return;
        }
        if (state.fadingOut) {
            state.fadingOut = false;
            state.gain.to(1, this.biomeConfig.fadeInNanos(), now);
        }
        if (state.phase == Phase.WAITING && session.track().pack().id().equals(state.baseId)) {
            if (state.baseReady) {
                this.startSound(player, state, session);
            } else if (state.baseFailed) {
                state.phase = Phase.DONE;
                player.sendActionBar((Component)Component.empty());
            }
        }
        if (state.phase == Phase.WAITING && now - state.waitingSince > TimeUnit.SECONDS.toNanos(session.settings().loadTimeout())) {
            state.phase = Phase.DONE;
            player.removeResourcePack(session.track().pack().id());
            player.sendActionBar((Component)Component.empty());
            player.sendMessage("Pack backsound belum berhasil dimuat. Periksa public-base-url/port dan pengaturan resource pack klien.");
        }
        if (state.phase == Phase.PLAYING) this.positionSound(player, state, now);
        if (state.phase == Phase.PLAYING && now >= state.end) {
            player.stopSound(session.track().sound(), SoundCategory.RECORDS);
            if (session == state.bossSession && state.baseReady) {
                state.bossTrackIndex++;
                state.bossSession = null;
                state.phase = Phase.DONE;
            } else if (session == state.biomeSession && state.baseReady) {
                state.biomeTrackIndex++;
                state.biomeSession = null;
                state.phase = Phase.DONE;
            } else {
                state.phase = Phase.DONE;
                player.sendActionBar((Component)Component.empty());
            }
        }
        if (state.phase == Phase.DONE) {
            return;
        }
        // Sound-source movement runs every tick; retain the original 5 Hz UI.
        if (now - state.lastUi < 200_000_000L) return;
        state.lastUi = now;
        long elapsed = state.phase == Phase.PLAYING ? Math.max(0L, (now - state.start) / 1000000000L) : 0L;
        String title = YouTubeBacksound.marquee(session.track().title(), (now - session.created()) / 200000000L);
        TextComponent icon = state.baseReady ? ((TextComponent)Component.text((String)CassetteIcon.glyph(state.phase == Phase.PLAYING ? (now - state.start) / 200000000L : 0L)).font(Key.key((String)"ytbacksound", (String)"cassette"))).color((TextColor)NamedTextColor.WHITE) : Component.text((String)"\u266b", (TextColor)NamedTextColor.GOLD);
        player.sendActionBar(icon.append(Component.text((String)(" [" + state.playbackLabel + "] " + title + " " + (String)(state.phase == Phase.WAITING ? "Memuat pack\u2026" : YouTubeBacksound.time(elapsed) + "/" + YouTubeBacksound.time((long)Math.ceil(session.track().seconds())))), TextColor.color(state.biomeColor)).font(Key.key((String)"minecraft", (String)"default"))));
    }

    private void offerBase(Player player, Viewer state) {
        LocalLibrary prepared = this.library;
        if (prepared == null || this.settings.publicUrl().contains("IP-SERVER")) {
            return;
        }
        if (!prepared.pack().id().equals(state.baseId) || !this.settings.publicUrl().equals(state.baseUrl)) {
            if (state.baseId != null) {
                player.removeResourcePack(state.baseId);
            }
            state.baseId = prepared.pack().id();
            state.baseUrl = this.settings.publicUrl();
            state.baseReady = false;
            state.baseFailed = false;
            state.baseOffered = System.nanoTime();
            player.addResourcePack(state.baseId, state.baseUrl + "/packs/" + String.valueOf(state.baseId) + ".zip", prepared.pack().sha1(), "Backsound: semua lagu dan ikon kaset", false);
        }
        if (!state.baseReady && !state.baseFailed && System.nanoTime() - state.baseOffered > TimeUnit.SECONDS.toNanos(this.settings.loadTimeout())) {
            state.baseFailed = true;
            player.removeResourcePack(state.baseId);
            player.sendMessage("Resource pack musik belum termuat. Periksa URL/port publik dan izinkan Server Resource Packs, lalu masuk ulang.");
        }
    }

    private void installBiomeConfiguration() throws Exception {
        Path config = this.getDataFolder().toPath().resolve("biome-music.yml");
        if (Files.exists(config)) {
            var previous = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());
            if (previous.getString("bundled-audio-version", "").equals("2.5.0")) {
                boolean changed = false;
                if (!previous.contains("fade-out-seconds")) { previous.set("fade-out-seconds", 2.0); changed = true; }
                if (!previous.contains("fade-in-seconds")) { previous.set("fade-in-seconds", 1.5); changed = true; }
                if (changed) previous.save(config.toFile());
                return;
            }
            Path backup = config.resolveSibling("biome-music.before-2.5.0.yml");
            if (!Files.exists(backup)) Files.copy(config, backup);
        }
        try (var input = this.getResource("biome-music.yml")) {
            if (input == null) throw new java.io.IOException("Bundled biome config tidak tersedia");
            Files.copy(input, config, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        this.getLogger().info("Pemetaan audio biome 2.5.0 diterapkan; config lama dicadangkan.");
    }

    private void installBundledMusic() throws Exception {
        Path music = this.getDataFolder().toPath().resolve("music");
        Files.createDirectories(music);
        try (var input = this.getResource("bundled-audio.txt")) {
            if (input == null) throw new java.io.IOException("Daftar bundled audio tidak tersedia");
            for (String name : new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                if (name.isBlank()) continue;
                Settings.validateFile(name);
                Path target = music.resolve(name);
                if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                try (var audio = this.getResource("bundled-music/" + name)) {
                    if (audio == null) throw new java.io.IOException("Bundled audio hilang: " + name);
                    Path temp = Files.createTempFile(music, ".install-", ".tmp");
                    try {
                        Files.copy(audio, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        Files.move(temp, target);
                    } finally { Files.deleteIfExists(temp); }
                }
            }
        }
    }

    private void positionSound(Player player, Viewer state, long now) {
        SpatialSoundBridge.Channel channel = state.channel;
        if (channel == null) return;
        var position = player.getEyeLocation().add(0, FadeEnvelope.distance(state.gain.value(now)), 0);
        if (!channel.move(position)) {
            channel.close(); state.channel = null;
            // A failed runtime binding is reported once; preserve basic audio.
            if (state.seen != null && state.volume > 0 && state.phase == Phase.PLAYING) {
                player.stopSound(state.seen.track().sound(), SoundCategory.RECORDS);
                this.startSound(player, state, state.seen);
            }
        }
    }

    private void startSound(Player player, Viewer state, Session session) {
        if (this.closed || !state.live) return;
        state.start = System.nanoTime();
        state.end = state.start + (long)(session.track().seconds() * 1.0E9);
        state.phase = Phase.PLAYING;
        state.fadingOut = false;
        if (state.volume <= 0) return;
        state.gain.set(this.biomeConfig.fadeInNanos() > 0 ? .02 : 1);
        var position = player.getEyeLocation().add(0, FadeEnvelope.distance(state.gain.value(state.start)), 0);
        SpatialSoundBridge.Channel channel = state.channel;
        if (channel == null) {
            channel = this.spatialSound.open(player, position);
            state.channel = channel;
        }
        if (this.closed || !state.live) {
            if (channel != null) channel.close();
            state.channel = null;
            return;
        }
        if (channel != null) {
            if (channel.move(position) && channel.play(session.track().sound(), state.volume / 100f)) {
                state.gain.to(1, this.biomeConfig.fadeInNanos(), state.start);
                return;
            }
            channel.close(); state.channel = null;
        }
        if (this.closed || !state.live) return;
        state.gain.set(1);
        player.playSound((Entity)player, session.track().sound(), SoundCategory.RECORDS, state.volume / 100.0f, 1.0f);
    }

    @EventHandler
    public void packStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID id = event.getID();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        player.getScheduler().run((Plugin)this, task -> {
            if (this.closed) {
                return;
            }
            Viewer state = (Viewer)this.viewers.get(player.getUniqueId());
            if (state == null) {
                return;
            }
            if (id.equals(state.baseId)) {
                if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED && !state.baseFailed) {
                    state.baseReady = true;
                } else if (!Set.of("ACCEPTED", "DOWNLOADED", "SUCCESSFULLY_LOADED").contains(status.name())) {
                    state.baseReady = false;
                    state.baseFailed = true;
                    player.sendMessage("Resource pack musik: " + status.name() + ". Aktifkan Server Resource Packs agar audio dan kaset tampil.");
                }
                return;
            }
            Session session = state.seen;
            if (session == null || state.seen != session || !session.track().pack().id().equals(id) || state.phase != Phase.WAITING) {
                return;
            }
            if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                this.startSound(player, state, session);
            } else if (!status.name().equals("ACCEPTED") && !status.name().equals("DOWNLOADED")) {
                state.phase = Phase.DONE;
                player.removeResourcePack(id);
                player.sendActionBar((Component)Component.empty());
                player.sendMessage("Backsound tidak diputar: resource pack " + status.name() + ".");
            }
        }, null);
    }

    private void tick() {
        Session session = this.current;
        if (session == null) {
            return;
        }
        long now = System.nanoTime();
        boolean pending = session.cohort().isEmpty() ? now - session.created() < (long)(session.track().seconds() * 1.0E9) : false;
        for (UUID id : session.cohort()) {
            Viewer state = (Viewer)this.viewers.get(id);
            if (state == null || !state.live) continue;
            if (state.biomeSession != null && state.seen == state.biomeSession) continue;
            if (state.bossSession != null && state.seen == state.bossSession) continue;
            if (state.seen != session || state.phase == Phase.IDLE || state.phase == Phase.WAITING) {
                if (now - session.created() >= TimeUnit.SECONDS.toNanos(session.settings().loadTimeout()) + 1000000000L) continue;
                pending = true;
                continue;
            }
            if (state.phase != Phase.PLAYING || now >= state.end) continue;
            pending = true;
        }
        if (!pending) {
            this.next();
        }
    }

    private void invalidate() {
        ++this.generation;
        this.current = null;
        if (this.http != null) {
            this.http.active = null;
        }
        this.loading = false;
        if (this.job != null) {
            this.job.cancel(true);
            this.job = null;
        }
        if (this.worker != null) {
            this.worker.purge();
        }
    }

    private void stop() {
        this.invalidate();
        this.queue.clear();
        this.index = 0;
        this.total = 0;
    }

    private void next() {
        this.invalidate();
        if (this.queue.isEmpty()) {
            return;
        }
        Settings.Source source = this.queue.removeFirst();
        Settings snapshot = this.settings;
        long token = this.generation;
        this.loading = true;
        this.job = this.worker.submit(() -> {
            Path temporary = null;
            try {
                temporary = this.extractor.newJob();
                if (!source.local() && YouTubeUrl.parse(source.value()).videoId() == null) {
                    List<Settings.Source> expanded = this.extractor.expand(source, snapshot, temporary);
                    this.global(() -> {
                        if (token != this.generation) {
                            return;
                        }
                        if (expanded.size() + this.queue.size() > this.settings.maxQueue()) {
                            this.announce("Playlist melebihi kapasitas antrean; dilewati.");
                            --this.total;
                            this.next();
                            return;
                        }
                        this.total += expanded.size() - 1;
                        for (int i = expanded.size() - 1; i >= 0; --i) {
                            this.queue.addFirst((Settings.Source)expanded.get(i));
                        }
                        this.next();
                    });
                } else {
                    LocalLibrary prepared = this.library;
                    MediaExtractor.Track cached = prepared == null ? null : prepared.find(source);
                    MediaExtractor.Track track = cached == null ? this.extractor.prepare(source, snapshot, temporary) : cached;
                    this.global(() -> {
                        if (token != this.generation) {
                            return;
                        }
                        this.loading = false;
                        this.job = null;
                        ++this.index;
                        Session ready = new Session(track, snapshot, System.nanoTime(), Set.copyOf(this.viewers.keySet()), this.index, Math.max(this.index, this.total));
                        this.http.active = track.pack();
                        this.current = ready;
                        this.announce("Memutar: " + track.title());
                    });
                }
            }
            catch (Exception ex) {
                this.global(() -> {
                    if (token != this.generation) {
                        return;
                    }
                    if (ex instanceof MediaExtractor.MissingProgramException) {
                        this.stop();
                        this.announce(ex.getMessage() + " Antrean dihentikan. Jalankan /ytmusic check; admin hosting perlu memasang program tersebut.");
                        return;
                    }
                    ++this.index;
                    this.announce("Lagu gagal dimuat, dilewati: " + ex.getMessage());
                    this.next();
                });
            }
            finally {
                if (temporary != null) {
                    try {
                        this.extractor.clean(temporary);
                    }
                    catch (Exception ex) {
                        this.getLogger().warning("Cleanup file kerja gagal: " + ex.getMessage());
                    }
                }
            }
        });
    }

    private void enqueue(CommandSender sender, List<Settings.Source> sources, boolean replace) {
        if (this.settings.publicUrl().contains("IP-SERVER")) {
            this.reply(sender, "Atur public-base-url di config lalu /ytmusic reload.");
            return;
        }
        if (sources.isEmpty()) {
            this.reply(sender, "Playlist kosong.");
            return;
        }
        if (sources.size() + (replace ? 0 : this.queue.size()) > this.settings.maxQueue()) {
            this.reply(sender, "Antrean melebihi batas.");
            return;
        }
        if (replace) {
            this.stop();
        }
        if (this.current == null && !this.loading && this.queue.isEmpty()) {
            this.index = 0;
            this.total = 0;
        }
        this.queue.addAll(sources);
        this.total += sources.size();
        this.reply(sender, "Ditambahkan ke antrean: " + sources.size() + " lagu/sumber.");
        if (this.current == null && !this.loading) {
            this.next();
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rpgboss")) {
            RpgBossController controller = this.rpgBoss;
            if (controller == null) { sender.sendMessage("Fitur RPG memerlukan MythicMobs aktif; periksa console."); return true; }
            return controller.command(sender, args);
        }
        if (command.getName().equalsIgnoreCase("musicgui") || (args.length >= 1 && Set.of("volume", "gui").contains(args[0].toLowerCase(Locale.ROOT)))) {
            if (!(sender instanceof Player player)) { sender.sendMessage("GUI volume hanya untuk pemain."); return true; }
            if (!sender.hasPermission("backsound.volume")) { sender.sendMessage("Tidak punya izin backsound.volume."); return true; }
            player.getScheduler().run(this, task -> { if (!this.closed) this.openVolume(player); }, null);
            return true;
        }
        if (!sender.hasPermission("backsound.use")) {
            sender.sendMessage("Anda memerlukan permission backsound.use.");
            return true;
        }
        if (args.length == 0) {
            this.help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            if (sub.equals("play") && args.length == 2) {
                Settings.Source selected = Settings.resolve(args[1], this.settings.songs());
                this.global(() -> this.enqueue(sender, List.of(selected), false));
            } else if (sub.equals("file") && args.length >= 2) {
                Settings.validateFile(args[1]);
                String title = Settings.title(args.length > 2 ? String.join((CharSequence)" ", Arrays.copyOfRange(args, 2, args.length)) : args[1]);
                Settings.Source source = new Settings.Source(args[1], title, true);
                this.global(() -> this.enqueue(sender, List.of(source), false));
            } else if (sub.equals("playlist") && args.length == 1) {
                this.global(() -> this.enqueue(sender, this.settings.playlist().stream().map(value -> Settings.resolve(value, this.settings.songs())).toList(), true));
            } else if (sub.equals("skip") && args.length == 1) {
                this.global(() -> {
                    this.next();
                    this.reply(sender, "Melompati lagu aktif/unduhan.");
                });
            } else if (sub.equals("stop") && args.length == 1) {
                this.global(() -> {
                    this.stop();
                    this.reply(sender, "Musik dan antrean dihentikan.");
                });
            } else if (sub.equals("queue") && args.length == 1) {
                this.global(() -> {
                    this.reply(sender, "Aktif: " + (this.loading ? "memproses audio" : (this.current == null ? "-" : this.current.track().title())));
                    int i = 1;
                    for (Settings.Source source : this.queue) {
                        this.reply(sender, i++ + ". " + (source.title().isBlank() ? source.value() : source.title()));
                    }
                    if (this.queue.isEmpty()) {
                        this.reply(sender, "Antrean berikutnya kosong.");
                    }
                });
            } else if (sub.equals("check") && args.length == 1) {
                this.check(sender);
            } else if (sub.equals("reload") && args.length == 1) {
                this.reload(sender);
            } else {
                this.help(sender);
            }
        }
        catch (IllegalArgumentException ex) {
            sender.sendMessage("Input tidak valid: " + ex.getMessage());
        }
        return true;
    }

    private void check(CommandSender sender) {
        if (!this.checking.compareAndSet(false, true)) {
            sender.sendMessage("Pemeriksaan masih berjalan.");
            return;
        }
        this.global(() -> this.worker.submit(() -> {
            Path temporary = null;
            try {
                temporary = this.extractor.newJob();
                Settings snapshot = this.settings;
                List<String> report = MediaExtractor.diagnose(snapshot, temporary);
                this.global(() -> {
                    this.reply(sender, "HTTP bind " + snapshot.bind() + ":" + snapshot.port() + "; URL " + snapshot.publicUrl());
                    this.reply(sender, "Akses URL dari komputer pemain belum diperiksa oleh command ini.");
                    for (String line : report) {
                        this.reply(sender, line);
                    }
                    this.reply(sender, "Tanpa program tambahan: upload OGG Vorbis ke music, lalu /ytmusic file nama.ogg.");
                });
            }
            catch (Exception ex) {
                this.global(() -> this.reply(sender, "Pemeriksaan gagal: " + ex.getMessage()));
            }
            finally {
                if (temporary != null) {
                    try {
                        this.extractor.clean(temporary);
                    }
                    catch (Exception ex) {
                        this.getLogger().warning(ex.getMessage());
                    }
                }
                this.checking.set(false);
            }
        }));
    }

    private void reload(CommandSender sender) {
        if (!this.reloading.compareAndSet(false, true)) {
            sender.sendMessage("Reload masih berjalan.");
            return;
        }
        this.global(() -> this.worker.submit(() -> {
            try {
                Settings replacement = Settings.read(this.getDataFolder().toPath().resolve("config.yml"));
                if (!replacement.bind().equals(this.settings.bind()) || replacement.port() != this.settings.port()) {
                    this.global(() -> this.reply(sender, "Perubahan http-bind/http-port memerlukan restart; config belum diterapkan."));
                    return;
                }
                BiomeConfig replacementBiome = this.readBiomeConfig();
                BossMusicSettings replacementBoss = BossMusicSettings.read(this.getDataFolder().toPath().resolve("boss-music.yml"));
                LocalLibrary updatedLibrary = LocalLibrary.build(this.getDataFolder().toPath(), replacement);
                if (this.closed) {
                    return;
                }
                Files.write(this.getDataFolder().toPath().resolve("resourcepack.zip"), updatedLibrary.pack().zip(), new OpenOption[0]);
                this.global(() -> {
                    if (!replacement.bind().equals(this.settings.bind()) || replacement.port() != this.settings.port()) {
                        this.reply(sender, "Perubahan http-bind/http-port memerlukan restart; config belum diterapkan.");
                        return;
                    }
                    this.stop();
                    this.biomeConfig = replacementBiome;
                    this.bossSettings = replacementBoss;
                    if (this.bossHook != null) this.bossHook.rescan();
                    this.settings = replacement;
                    this.library = updatedLibrary;
                    this.http.library = updatedLibrary.pack();
                    this.reply(sender, "Config dan resource pack gabungan diperbarui. Jalankan /ytmusic playlist untuk memutar kembali.");
                });
            }
            catch (Exception ex) {
                this.global(() -> this.reply(sender, "Reload gagal; config lama dipertahankan: " + ex.getMessage()));
            }
            finally {
                this.reloading.set(false);
            }
        }));
    }

    private void help(CommandSender sender) {
        sender.sendMessage("/ytmusic play <URL YouTube/playlist/id> | playlist | file <nama.ogg/mp3/wav> [judul] | skip | stop | queue | reload | check");
        sender.sendMessage("Volume pribadi: /musicgui atau /ytmusic volume");
        sender.sendMessage("ID config: " + String.join((CharSequence)", ", this.settings.songs().keySet()));
    }

    private void announce(String text) {
        for (UUID id : this.viewers.keySet()) {
            Player player = this.getServer().getPlayer(id);
            if (player == null) continue;
            this.reply((CommandSender)player, text);
        }
    }

    private void reply(CommandSender sender, String text) {
        if (this.closed) {
            return;
        }
        if (sender instanceof Player) {
            Player player = (Player)sender;
            player.getScheduler().run((Plugin)this, task -> {
                if (!this.closed) {
                    player.sendMessage(text);
                }
            }, null);
        } else {
            sender.sendMessage(text);
        }
    }

    private void global(Runnable action) {
        block3: {
            if (this.closed) {
                return;
            }
            try {
                this.getServer().getGlobalRegionScheduler().execute((Plugin)this, () -> {
                    if (!this.closed) {
                        action.run();
                    }
                });
            }
            catch (IllegalPluginAccessException ex) {
                if (this.closed) break block3;
                throw ex;
            }
        }
    }

    static String marquee(String title, long frame) {
        int[] points = ("   " + title + "   \u2726   ").codePoints().toArray();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 26; ++i) {
            text.appendCodePoint(points[Math.floorMod(frame + (long)i, points.length)]);
        }
        return text.toString();
    }

    static String time(long seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("rpgboss")) {
            if (args.length == 1) return (sender.hasPermission("rpgboss.admin") ? List.of("menu", "status", "toggle", "spawn", "reload") : List.of("menu", "status", "toggle")).stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            if (args.length == 2 && args[0].equalsIgnoreCase("spawn") && sender.hasPermission("rpgboss.admin")) return Arrays.stream(RpgTheme.values()).map(v -> v.key).filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            return List.of();
        }
        if (!sender.hasPermission("backsound.use")) {
            return args.length == 1 ? List.of("volume").stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
        }
        if (args.length == 1) {
            return List.of("play", "playlist", "file", "skip", "stop", "queue", "reload", "check", "volume").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            return this.settings.songs().keySet().stream().filter(s -> s.startsWith(args[1])).toList();
        }
        return List.of();
    }

    public void onDisable() {
        this.closed = true;
        if (this.rpgBoss != null) { this.rpgBoss.close(); this.rpgBoss = null; }
        if (this.bossHook != null) { this.bossHook.close(); this.bossHook = null; }
        this.encounters.clear();
        this.stop();
        if (this.monitor != null) {
            this.monitor.cancel();
        }
        this.viewers.values().forEach(this::retire);
        this.viewers.clear();
        if (this.worker != null) {
            this.worker.shutdownNow();
        }
        if (this.http != null) {
            this.http.close();
        }
        this.library = null;
        this.getServer().getGlobalRegionScheduler().cancelTasks((Plugin)this);
    }

    private static final class Viewer {
        volatile Session bossSession;
        UUID bossId;
        BossMusicSettings.Profile bossProfile;
        LocalLibrary bossLibrary;
        int bossTrackIndex;
        String playbackLabel = "Loading";
        final FadeEnvelope gain = new FadeEnvelope();
        volatile SpatialSoundBridge.Channel channel;
        boolean fadingOut;
        int biomeTrackIndex;
        String biomeGroup;
        BiomePolicy.Profile biomeProfile;
        int volume = -1;
        int lastVolume = 70;
        String biomeName = "Loading";
        int biomeColor = 0xFFFFFF;
        String displayKey;
        boolean titlePending;
        long pendingTitleAt;
        volatile Session biomeSession;
        LocalLibrary biomeLibrary;
        volatile boolean live = true;
        volatile ScheduledTask task;
        volatile Session seen;
        volatile Phase phase = Phase.IDLE;
        volatile long end;
        long start;
        long frame = -1L;
        UUID baseId;
        String baseUrl;
        boolean baseReady;
        boolean baseFailed;
        long baseOffered;
        long waitingSince;
        long lastUi;

        private Viewer() {
        }
    }

    private record Session(MediaExtractor.Track track, Settings settings, long created, Set<UUID> cohort, int index, int total) {
    }

    private static enum Phase {
        IDLE,
        WAITING,
        PLAYING,
        DONE;

    }
}

