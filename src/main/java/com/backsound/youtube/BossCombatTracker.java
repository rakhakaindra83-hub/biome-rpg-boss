package com.backsound.youtube;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Entity-free snapshots allow encounters to span Folia regions safely. */
final class BossCombatTracker {
 record Position(UUID world,double x,double y,double z) {
  double distanceSquared(Position other) {
   if (!world.equals(other.world)) return Double.POSITIVE_INFINITY;
   double dx=x-other.x,dy=y-other.y,dz=z-other.z;return dx*dx+dy*dy+dz*dz;
  }
 }
 record Boss(UUID id,String type,Position position,long updatedAt) {}
 private final ConcurrentMap<UUID,Boss> bosses=new ConcurrentHashMap<>();
 private final ConcurrentMap<UUID,ConcurrentMap<UUID,Long>> players=new ConcurrentHashMap<>();
 void update(UUID boss,String type,Position position,long now) {bosses.put(boss,new Boss(boss,type,position,now));}
 void engage(UUID player,UUID boss,long now) {
  if(bosses.containsKey(boss)) players.computeIfAbsent(player,k->new ConcurrentHashMap<>()).merge(boss,now,Math::max);
 }
 boolean known(UUID id) {return bosses.containsKey(id);}
 void remove(UUID boss) {bosses.remove(boss);players.values().forEach(m->m.remove(boss));}
 void clearPlayer(UUID player) {players.remove(player);}
 void clear() {bosses.clear();players.clear();}
 Boss select(UUID player,Position position,long now,long timeout,double range,Predicate<String> ready) {
  var marks=players.get(player);if(marks==null)return null;
  Boss selected=null;long newest=Long.MIN_VALUE;
  for(var mark:marks.entrySet()) {
   Boss boss=bosses.get(mark.getKey());
   if(boss==null || now-mark.getValue()>timeout || !boss.position.world.equals(position.world)) {marks.remove(mark.getKey(),mark.getValue());continue;}
   if(now-boss.updatedAt>6_000_000_000L || boss.position.distanceSquared(position)>range*range || !ready.test(boss.type))continue;
   if(selected==null || mark.getValue()>newest || (mark.getValue()==newest && boss.id.compareTo(selected.id)<0)) {selected=boss;newest=mark.getValue();}
  }
  return selected;
 }
}
