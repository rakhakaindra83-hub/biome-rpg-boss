package com.backsound.youtube;
import java.util.*;
/** Atomic reservations prevent independent Folia regions exceeding spawn caps. */
final class SpawnLedger {
 record Position(UUID world,double x,double y,double z){double distanceSquared(Position p){if(!world.equals(p.world))return Double.POSITIVE_INFINITY;double dx=x-p.x,dy=y-p.y,dz=z-p.z;return dx*dx+dy*dy+dz*dz;}}
 record Limits(int global,int perWorld,double spacing,int cellSize,long areaCooldown,long playerCooldown){}
 private record Reservation(UUID owner,Position position){}
 private final Map<UUID,Position> active=new HashMap<>();
 private final Map<UUID,Reservation> pending=new HashMap<>();
 private final Map<String,Long> cooldowns=new HashMap<>();
 private static String playerKey(UUID id){return "player:"+id;}
 static String areaKey(Position p,int size){return "area:"+p.world+":"+(int)Math.floor(p.x/size)+":"+(int)Math.floor(p.z/size);}
 synchronized boolean reserve(UUID ticket,UUID owner,Position p,long now,Limits l,boolean manual){
  if(pending.containsKey(ticket)||active.size()+pending.size()>=l.global)return false;
  long count=active.values().stream().filter(a->a.world.equals(p.world)).count()+pending.values().stream().filter(a->a.position.world.equals(p.world)).count();
  if(count>=l.perWorld)return false;
  if(active.values().stream().anyMatch(a->a.distanceSquared(p)<l.spacing*l.spacing)||pending.values().stream().anyMatch(a->a.position.distanceSquared(p)<l.spacing*l.spacing))return false;
  if(!manual&&(cooldowns.getOrDefault(areaKey(p,l.cellSize),0L)>now||cooldowns.getOrDefault(playerKey(owner),0L)>now))return false;
  pending.put(ticket,new Reservation(owner,p));return true;
 }
 synchronized boolean commit(UUID ticket,UUID boss,Position p,long now,Limits l){Reservation r=pending.remove(ticket);if(r==null)return false;active.put(boss,p);cooldowns.put(areaKey(p,l.cellSize),now+l.areaCooldown);cooldowns.put(playerKey(r.owner),now+l.playerCooldown);return true;}
 synchronized void release(UUID ticket){pending.remove(ticket);}
 synchronized void track(UUID boss,Position p){active.put(boss,p);}
 synchronized void remove(UUID boss){active.remove(boss);}
 synchronized int count(){return active.size();}
 synchronized int pendingCount(){return pending.size();}
 synchronized Map<String,Long> cooldownSnapshot(long now){cooldowns.entrySet().removeIf(e->e.getValue()<=now);return Map.copyOf(cooldowns);}
 synchronized void restore(Map<String,Long> values,long now){values.forEach((k,v)->{if(v>now&&v<now+31L*24*60*60*1000)cooldowns.put(k,v);});}
 synchronized void clear(){pending.clear();active.clear();}
}
