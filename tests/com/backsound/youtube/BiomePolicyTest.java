package com.backsound.youtube;
import java.util.*;
import java.nio.file.*;
public final class BiomePolicyTest {
 static int checks;
 static void eq(Object got,Object want) { checks++; if(!Objects.equals(got,want)) throw new AssertionError(got+" != "+want); }
 static String route(String b,String env,int y,int light,boolean water) {return BiomePolicy.group(b,env,y,light,water,48);}
 public static void main(String[] args) throws Exception {
  eq(route("taiga","NORMAL",70,15,false),"taiga");
  eq(route("old_growth_spruce_taiga","NORMAL",70,15,false),"taiga");
  eq(route("snowy_taiga","NORMAL",70,15,false),"snow");
  eq(route("desert","NORMAL",70,15,false),"desert");
  eq(route("desert","NORMAL",15,0,false),"cave");
  eq(route("lush_caves","NORMAL",15,0,false),"lush-caves");
  eq(route("deep_dark","NORMAL",-45,0,false),"deep-dark");
  eq(route("ocean","NORMAL",15,0,true),"cave");
  eq(route("plains","NORMAL",49,0,false),"plains");
  eq(route("plains","NORMAL",48,0,false),"cave");
  for (int light : List.of(0,7,15)) for (boolean water : List.of(false,true)) {
    eq(route("desert","NORMAL",48,light,water),"cave");
    eq(route("ocean","NORMAL",20,light,water),"cave");
    eq(route("plains","NORMAL",49,light,water),"plains");
  }
  eq(route("dripstone_caves","NORMAL",60,0,false),"plains");
  eq(route("crimson_forest","NETHER",15,0,false),"nether");
  eq(route("end_highlands","THE_END",15,0,false),"end");
  eq(route("stony_shore","NORMAL",70,15,false),"beach");
  eq(route("flower_forest","NORMAL",70,15,false),"forest");
  eq(route("unknown_custom","NORMAL",70,15,false),"plains");
  eq(BiomePolicy.clampVolume(-10),0);eq(BiomePolicy.clampVolume(110),100);
  eq(BiomePolicy.displayName("old_growth_pine_taiga"),"Old Growth Pine Taiga");
  for(int n : List.of(1,3,6,7)) {
   for(int i=0;i<n*4;i++) eq(BiomePolicy.playlistIndex(i,n),i%n);
   eq(BiomePolicy.playlistIndex(-1,n),n-1);
  }
  boolean invalid=false;try{BiomePolicy.playlistIndex(0,0);}catch(IllegalArgumentException e){invalid=true;}eq(invalid,true);
  for(String b:Files.readAllLines(Path.of("tests/biomes.txt"))) for(String env:List.of("NORMAL","NETHER","THE_END")) eq(BiomePolicy.PROFILES.containsKey(route(b,env,80,15,false)),true);
  System.out.println("PASS: "+checks+" biome routing, taiga/snow, cave priority, volume, playlist wrap and label checks");
 }
}
