package com.backsound.youtube;
import java.util.*;
enum RpgTheme {
 FROST("frost","RPG_FrostWarden","Frost Warden",0x8AE7FF,"DIAMOND_SWORD"),
 DESERT("desert","RPG_DuneTyrant","Dune Tyrant",0xF4CC76,"DIAMOND_AXE"),
 CAVE("cave","RPG_AbyssSentinel","Abyss Sentinel",0x4EDAD5,"DIAMOND_SWORD"),
 FOREST("forest","RPG_AncientTreant","Ancient Treant",0x96D35C,"NETHERITE_AXE"),
 OCEAN("ocean","RPG_TideReaver","Tide Reaver",0x61DBF5,"TRIDENT"),
 NETHER("nether","RPG_EmberOverlord","Ember Overlord",0xFF9150,"NETHERITE_SWORD");
 final String key,id,title,material;final int color;
 RpgTheme(String key,String id,String title,int color,String material){this.key=key;this.id=id;this.title=title;this.color=color;this.material=material;}
 static RpgTheme find(String value){return Arrays.stream(values()).filter(t->t.key.equalsIgnoreCase(value)||t.id.equalsIgnoreCase(value)).findFirst().orElse(null);}
 static RpgTheme forGroup(String group){return switch(group){case "snow","taiga"->FROST;case "desert","badlands","savanna"->DESERT;case "cave","lush-caves","deep-dark"->CAVE;case "ocean","river","beach"->OCEAN;case "nether"->NETHER;case "end"->null;default->FOREST;};}
}
