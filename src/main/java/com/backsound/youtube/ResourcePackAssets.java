package com.backsound.youtube;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.security.MessageDigest;
final class ResourcePackAssets {
 static PackBuilder.Pack merge(PackBuilder.Pack base,Path assets)throws Exception{
  if(!Files.isRegularFile(assets))return base;
  var entries=new TreeMap<String,byte[]>();
  try(var in=new ZipInputStream(new ByteArrayInputStream(base.zip()))){for(ZipEntry e;(e=in.getNextEntry())!=null;)if(!e.isDirectory())entries.put(e.getName(),in.readAllBytes());}
  try(var in=new ZipInputStream(Files.newInputStream(assets))){for(ZipEntry e;(e=in.getNextEntry())!=null;){String name=e.getName();if(e.isDirectory()||!name.startsWith("assets/rpgboss/"))continue;if(name.contains("..")||name.contains("\\"))throw new IOException("Path aset tidak valid");byte[] bytes=in.readNBytes(4*1024*1024+1);if(bytes.length>4*1024*1024)throw new IOException("Aset terlalu besar");entries.put(name,bytes);}}
  var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){for(var e:entries.entrySet()){ZipEntry item=new ZipEntry(e.getKey());item.setTime(0);zip.putNextEntry(item);zip.write(e.getValue());zip.closeEntry();}}
  byte[] data=out.toByteArray(),hash=MessageDigest.getInstance("SHA-1").digest(data);return new PackBuilder.Pack(UUID.nameUUIDFromBytes(hash),data,hash);
 }
}
