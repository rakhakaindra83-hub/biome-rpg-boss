/*
 * Decompiled with CFR 0.152.
 */
package com.backsound.youtube;

import com.backsound.youtube.MediaExtractor;
import com.backsound.youtube.OggAudio;
import com.backsound.youtube.PackBuilder;
import com.backsound.youtube.Settings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

record LocalLibrary(PackBuilder.Pack pack, Map<String, MediaExtractor.Track> tracks) {
    static LocalLibrary build(Path data, Settings settings) throws Exception {
        LinkedHashMap<String, Settings.Source> sources = new LinkedHashMap<String, Settings.Source>();
        for (Settings.Source source : settings.songs().values()) {
            if (!source.local() || !source.value().toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;
            sources.put(source.value(), source);
        }
        for (String item : settings.playlist()) {
            Settings.Source source = Settings.resolve(item, settings.songs());
            if (!source.local() || !source.value().toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;
            sources.put(source.value(), source);
        }
        var biomeConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(data.resolve("biome-music.yml").toFile());
        var profiles = biomeConfig.getConfigurationSection("profiles");
        if (profiles != null) for (String group : profiles.getKeys(false)) {
            String prefix = group + ".";
            var selected = new java.util.ArrayList<String>(profiles.getStringList(prefix + "files"));
            selected.add(profiles.getString(prefix + "file", ""));
            selected.add(profiles.getString(prefix + "fallback", ""));
            for (String name : selected) {
                if (name.isBlank()) continue;
                Settings.validateFile(name);
                if (!name.toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;
                sources.putIfAbsent(name, new Settings.Source(name, profiles.getString(prefix + "title", group), true));
            }
        }
        var bossConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(data.resolve("boss-music.yml").toFile());
        var bossProfiles = bossConfig.getConfigurationSection("bosses");
        if (bossConfig.getBoolean("enabled", true) && bossProfiles != null) for (String id : bossProfiles.getKeys(false)) {
            String prefix = id + ".";
            if (!bossProfiles.getBoolean(prefix + "enabled", false)) continue;
            var files = new java.util.ArrayList<String>(bossProfiles.getStringList(prefix + "files"));
            if (files.isEmpty()) files.add(bossProfiles.getString(prefix + "file", ""));
            for (String name : files) {
                if (name.isBlank()) continue;
                Settings.validateFile(name);
                if (!name.toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;
                sources.putIfAbsent(name, new Settings.Source(name, bossProfiles.getString(prefix + "name", id), true));
            }
        }
        if (sources.size() > 500) throw new IOException("Maksimal 500 file audio dalam pack");
        if (sources.isEmpty()) {
            throw new IOException("Tidak ada OGG lokal untuk resource pack saat join");
        }
        Path root = data.resolve("music").toRealPath(new LinkOption[0]);
        TreeMap<String, byte[]> audio = new TreeMap<String, byte[]>();
        HashMap<String, Double> durations = new HashMap<String, Double>();
        long total = 0L;
        for (Settings.Source source : sources.values()) {
            byte[] bytes;
            MediaExtractor.interrupted();
            Settings.validateFile(source.value());
            Path candidate = root.resolve(source.value());
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
            Path file = candidate.toRealPath(new LinkOption[0]);
            if (!file.getParent().equals(root) || Files.size(file) > 0x2000000L) {
                throw new IOException("File pack harus di music dan maksimal 32 MiB");
            }
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);){
                bytes = input.readNBytes(0x2000001);
            }
            if ((total += (long)bytes.length) > 0x4000000L) {
                throw new IOException("Total audio pack melebihi 64 MiB");
            }
            durations.put(source.value(), OggAudio.duration(bytes, settings.maxDuration()));
            audio.put(source.value(), bytes);
        }
        if (audio.isEmpty()) throw new IOException("Belum ada file OGG lokal. Upload snowy.ogg ke folder music.");
        PackBuilder.Pack pack = ResourcePackAssets.merge(PackBuilder.bundle(audio), data.resolve("rpgboss-resourcepack.zip"));
        HashMap<String, MediaExtractor.Track> tracks = new HashMap<String, MediaExtractor.Track>();
        for (Settings.Source source : sources.values()) {
            if (!durations.containsKey(source.value())) continue;
            tracks.put(source.value(), new MediaExtractor.Track(source.title(), (Double)durations.get(source.value()), pack, PackBuilder.soundForFile(source.value())));
        }
        return new LocalLibrary(pack, Map.copyOf(tracks));
    }

    MediaExtractor.Track find(Settings.Source source) {
        MediaExtractor.Track track;
        MediaExtractor.Track track2 = track = source.local() ? this.tracks.get(source.value()) : null;
        return track == null ? null : new MediaExtractor.Track(source.title().isBlank() ? track.title() : source.title(), track.seconds(), this.pack, track.sound());
    }
}

