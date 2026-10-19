package com.backsound.youtube;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

record BossMusicSettings(boolean enabled,long timeout,double range,Map<String,Profile> profiles) {
 record Profile(String name,boolean enabled,List<String> files) {}
 static BossMusicSettings empty(){return new BossMusicSettings(true,20_000_000_000L,64,Map.of());}
 static BossMusicSettings read(Path path) throws Exception {
  YamlConfiguration yaml=new YamlConfiguration();yaml.load(path.toFile());
  double seconds=yaml.getDouble("combat-timeout-seconds",20),range=yaml.getDouble("max-distance",64);
  if(!Double.isFinite(seconds)||seconds<1||seconds>300||!Double.isFinite(range)||range<8||range>128)throw new IllegalArgumentException("Boss timeout 1-300 detik, jarak 8-128 blok");
  var result=new HashMap<String,Profile>();var section=yaml.getConfigurationSection("bosses");
  if(section!=null)for(String type:section.getKeys(false)) {
   if(!type.matches("[A-Za-z0-9_-]{1,96}"))throw new IllegalArgumentException("ID boss tidak valid: "+type);
   String prefix=type+".";var files=new ArrayList<>(section.getStringList(prefix+"files"));
   if(files.isEmpty())files.add(section.getString(prefix+"file","boss-"+type.toLowerCase(Locale.ROOT)+".ogg"));
   if(files.size()>100)throw new IllegalArgumentException("Maksimal 100 audio per boss");
   for(String file:files){Settings.validateFile(file);if(!file.toLowerCase(Locale.ROOT).endsWith(".ogg"))throw new IllegalArgumentException("Audio boss harus OGG Vorbis");}
   result.put(type,new Profile(section.getString(prefix+"name",type),section.getBoolean(prefix+"enabled",false),List.copyOf(files)));
  }
  if(result.size()>500)throw new IllegalArgumentException("Maksimal 500 profil boss");
  return new BossMusicSettings(yaml.getBoolean("enabled",true),(long)(seconds*1_000_000_000L),range,Map.copyOf(result));
 }
}
