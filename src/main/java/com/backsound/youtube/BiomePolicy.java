package com.backsound.youtube;

import java.util.Locale;
import java.util.Map;

/** Pure routing logic: a default for every vanilla/custom biome. */
final class BiomePolicy {
    record Profile(String group, String name, int color, String file, String fallback, String title) {}
    static final Map<String, Profile> PROFILES = Map.ofEntries(
        profile("snow", "Snow", 0xB9EDFF, "snowy.ogg", "snow-theme.ogg", "Snowy"),
        profile("desert", "Desert", 0xF7CD70, "desert.ogg", "desert-theme.ogg", "Desert"),
        profile("cave", "Cave", 0x9BAAC4, "cave.ogg", "cave-theme.ogg", "Echoes Below"),
        profile("deep-dark", "Deep Dark", 0x36B9C8, "deep-dark.ogg", "", "Ancient Echoes"),
        profile("lush-caves", "Lush Caves", 0x92D88A, "lush-caves.ogg", "", "Hidden Garden"),
        profile("plains", "Plains", 0xA9DB78, "plains.ogg", "", "Open Fields"),
        profile("taiga", "Taiga", 0x64A99B, "biomepack-taiga.ogg", "", "Taiga"),
        profile("forest", "Forest", 0x58B878, "forest.ogg", "", "Woodland Light"),
        profile("jungle", "Jungle", 0x34CE73, "jungle.ogg", "", "Emerald Canopy"),
        profile("swamp", "Swamp", 0x8FAF63, "swamp.ogg", "", "Still Waters"),
        profile("savanna", "Savanna", 0xDFBC68, "savanna.ogg", "", "Golden Grass"),
        profile("badlands", "Badlands", 0xE18C59, "badlands.ogg", "", "Red Horizon"),
        profile("mountain", "Mountains", 0xC4C9D2, "mountain.ogg", "", "High Above"),
        profile("ocean", "Ocean", 0x5AAAF5, "ocean.ogg", "", "Blue Distance"),
        profile("beach", "Beach", 0xF2E1AA, "beach.ogg", "", "Shoreline"),
        profile("river", "River", 0x6FCDDD, "river.ogg", "", "Flowing Home"),
        profile("mushroom", "Mushroom Fields", 0xD79ADF, "mushroom.ogg", "", "Spore Waltz"),
        profile("nether", "Nether", 0xF17A62, "nether.ogg", "", "Ember Halls"),
        profile("end", "The End", 0xC59DF2, "end.ogg", "", "Distant Islands")
    );
    private static Map.Entry<String, Profile> profile(String group, String name, int color, String file, String fallback, String title) {
        return Map.entry(group, new Profile(group, name, color, file, fallback, title));
    }
    static String group(String biome, String environment, int y, int skyLight, boolean underwater, int caveMaxY) {
        String b = biome.toLowerCase(Locale.ROOT);
        if (environment.equals("NETHER")) return "nether";
        if (environment.equals("THE_END")) return "end";
        // Depth is authoritative, including water and skylight openings.
        if (y <= caveMaxY) {
            if (b.equals("deep_dark")) return "deep-dark";
            if (b.equals("lush_caves")) return "lush-caves";
            return "cave";
        }
        if (b.contains("snow") || b.contains("frozen") || b.equals("ice_spikes") || b.equals("grove") || b.equals("jagged_peaks")) return "snow";
        if (b.contains("desert")) return "desert";
        if (b.contains("badlands")) return "badlands";
        if (b.contains("jungle") || b.equals("bamboo_jungle")) return "jungle";
        if (b.contains("swamp")) return "swamp";
        if (b.contains("savanna")) return "savanna";
        if (b.contains("ocean")) return "ocean";
        if (b.contains("beach") || b.equals("stony_shore")) return "beach";
        if (b.contains("river")) return "river";
        if (b.contains("mushroom")) return "mushroom";
        if (b.contains("taiga")) return "taiga";
        if (b.contains("forest") || b.contains("cherry") || b.contains("pale_garden")) return "forest";
        if (b.contains("peak") || b.contains("hill") || b.contains("mountain") || b.contains("meadow")) return "mountain";
        return "plains";
    }
    static String displayName(String biome) {
        StringBuilder result = new StringBuilder();
        for (String word : biome.replace(':', '_').split("_")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
    static int playlistIndex(int index, int size) { if (size < 1) throw new IllegalArgumentException("Playlist kosong"); return Math.floorMod(index, size); }
    static int clampVolume(int volume) { return Math.max(0, Math.min(100, volume)); }
}
