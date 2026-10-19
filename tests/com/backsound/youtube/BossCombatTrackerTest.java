package com.backsound.youtube;
import java.util.*;
import java.util.concurrent.*;
public final class BossCombatTrackerTest {
 static int checks;
 static void yes(boolean test){checks++;if(!test)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception {
  BossCombatTracker t=new BossCombatTracker();UUID world=UUID.randomUUID(),otherWorld=UUID.randomUUID(),player=UUID.randomUUID(),bystander=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
  var here=new BossCombatTracker.Position(world,0,64,0);var far=new BossCombatTracker.Position(world,65,64,0);long ttl=20_000_000_000L;
  t.update(a,"Frost",here,0);
  yes(t.select(player,here,0,ttl,64,id->true)==null); // proximity alone never starts music
  t.engage(player,a,0);yes(t.select(player,here,0,ttl,64,id->true).id().equals(a));
  yes(t.select(bystander,here,0,ttl,64,id->true)==null);
  yes(t.select(player,here,0,ttl,64,id->false)==null); // disabled/missing audio leaves biome music
  yes(t.select(player,far,0,ttl,64,id->true)==null);
  yes(t.select(player,new BossCombatTracker.Position(world,64,64,0),0,ttl,64,id->true)!=null);
  t.update(b,"Ember",here,1);t.engage(player,b,1);
  yes(t.select(player,here,1,ttl,64,id->true).id().equals(b));
  t.remove(b);yes(t.select(player,here,1,ttl,64,id->true).id().equals(a));
  t.remove(a);yes(t.select(player,here,1,ttl,64,id->true)==null);
  t.update(a,"Frost",here,0);t.engage(player,a,0);
  yes(t.select(player,here,6_000_000_001L,ttl,64,id->true)==null); // stale unloaded entity
  t.update(a,"Frost",here,19_000_000_000L);
  yes(t.select(player,here,19_000_000_000L,ttl,64,id->true)!=null);
  yes(t.select(player,here,20_000_000_001L,ttl,64,id->true)==null);
  t.engage(player,a,21_000_000_000L);t.update(a,"Frost",here,21_000_000_000L);
  yes(t.select(player,new BossCombatTracker.Position(otherWorld,0,64,0),21_000_000_000L,ttl,64,id->true)==null);
  yes(t.select(player,here,21_000_000_000L,ttl,64,id->true)==null); // dimension change clears encounter
  t.engage(player,a,21_000_000_000L);t.clearPlayer(player);yes(t.select(player,here,21_000_000_000L,ttl,64,id->true)==null);
  t.engage(player,a,21_000_000_000L);t.clear();yes(!t.known(a));yes(t.select(player,here,21_000_000_000L,ttl,64,id->true)==null);
  // Independent Folia regions update snapshots while a participant queries.
  var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var latch=new CountDownLatch(1);
  Thread updater=new Thread(()->{try{latch.await();for(int i=0;i<2000;i++){t.update(a,"Frost",here,i);t.engage(player,a,i);if(i%7==0)t.remove(a);}}catch(Throwable e){failure.set(e);}});
  Thread reader=new Thread(()->{try{latch.await();for(int i=0;i<2000;i++){var selected=t.select(player,here,i,ttl,64,id->true);if(selected!=null&&!selected.id().equals(a))throw new AssertionError();}}catch(Throwable e){failure.set(e);}});
  updater.start();reader.start();latch.countDown();updater.join();reader.join();yes(failure.get()==null);
  System.out.println("PASS: "+checks+" combat isolation, death/despawn, timeout, range, missing audio and concurrent snapshot checks");
 }
}
