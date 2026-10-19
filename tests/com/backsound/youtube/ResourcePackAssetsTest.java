package com.backsound.youtube;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
public final class ResourcePackAssetsTest {
 static int checks;
 static void ok(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 static byte[] zip(Map<String,byte[]> entries)throws Exception{var out=new ByteArrayOutputStream();try(var z=new ZipOutputStream(out)){for(var e:entries.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}return out.toByteArray();}
 static Map<String,byte[]> read(byte[] data)throws Exception{Map<String,byte[]> entries=new HashMap<>();try(var z=new ZipInputStream(new ByteArrayInputStream(data))){for(ZipEntry e;(e=z.getNextEntry())!=null;)entries.put(e.getName(),z.readAllBytes());}return entries;}
 public static void main(String[] args)throws Exception{
  byte[] audio={79,103,103,83,1,2,3},metadata="original metadata".getBytes();
  var original=new PackBuilder.Pack(UUID.randomUUID(),zip(Map.of("pack.mcmeta",metadata,"assets/ytbacksound/audio.ogg",audio)),new byte[20]);
  var merged=ResourcePackAssets.merge(original,Path.of("rpg-assets/resources/rpgboss-resourcepack.zip"));var entries=read(merged.zip());
  ok(Arrays.equals(entries.get("pack.mcmeta"),metadata));ok(Arrays.equals(entries.get("assets/ytbacksound/audio.ogg"),audio));
  ok(entries.keySet().stream().filter(n->n.startsWith("assets/rpgboss/items/")).count()==18);
  ok(entries.keySet().stream().filter(n->n.startsWith("assets/rpgboss/models/")).count()==18);
  ok(Arrays.equals(merged.sha1(),MessageDigest.getInstance("SHA-1").digest(merged.zip())));ok(merged.id().equals(UUID.nameUUIDFromBytes(merged.sha1())));
  ok(Arrays.equals(merged.zip(),ResourcePackAssets.merge(original,Path.of("rpg-assets/resources/rpgboss-resourcepack.zip")).zip()));
  ok(ResourcePackAssets.merge(original,Path.of("/tmp/no-rpg-assets-such-file.zip"))==original);
  Path bad=Files.createTempFile("bad-rpg-assets-",".zip");try{Files.write(bad,zip(Map.of("assets/rpgboss/../minecraft/override.json",new byte[0])));boolean rejected=false;try{ResourcePackAssets.merge(original,bad);}catch(IOException expected){rejected=true;}ok(rejected);}finally{Files.deleteIfExists(bad);}
  System.out.println("PASS: "+checks+" resource-pack overlay, audio preservation, deterministic hash and path checks");
 }
}
