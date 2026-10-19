package com.backsound.youtube;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class SpawnLedgerTest {
 static int checks;
 static void ok(boolean test){checks++;if(!test)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception {
  UUID world=UUID.randomUUID(),p=UUID.randomUUID(),boss=UUID.randomUUID();var at=new SpawnLedger.Position(world,0,64,0);var limits=new SpawnLedger.Limits(6,2,100,256,1800000,1200000);var ledger=new SpawnLedger();UUID ticket=UUID.randomUUID();
  ok(ledger.reserve(ticket,p,at,0,limits,false));ok(ledger.pendingCount()==1);ok(!ledger.reserve(UUID.randomUUID(),UUID.randomUUID(),at,0,limits,false));
  ledger.release(ticket);ok(ledger.pendingCount()==0);ok(ledger.reserve(ticket,p,at,0,limits,false));
  // A spawn event can be observed before the spawn call returns/commits.
  ledger.track(boss,at);ok(ledger.commit(ticket,boss,at,0,limits));ok(ledger.count()==1&&ledger.pendingCount()==0);
  ledger.remove(boss);ok(!ledger.reserve(UUID.randomUUID(),UUID.randomUUID(),at,100,limits,false)); // area cooldown
  ok(!ledger.reserve(UUID.randomUUID(),p,new SpawnLedger.Position(world,1000,64,0),100,limits,false)); // player cooldown
  UUID manual=UUID.randomUUID();ok(ledger.reserve(manual,p,at,100,limits,true));ledger.release(manual);
  var saved=ledger.cooldownSnapshot(100);var restarted=new SpawnLedger();restarted.restore(saved,100);ok(!restarted.reserve(UUID.randomUUID(),UUID.randomUUID(),at,100,limits,false));
  ok(restarted.reserve(UUID.randomUUID(),UUID.randomUUID(),at,1800000,limits,false));
  ok(!SpawnLedger.areaKey(new SpawnLedger.Position(world,-.1,64,0),256).equals(SpawnLedger.areaKey(at,256)));
  // Parallel region requests must not exceed global or per-world limits.
  var concurrent=new SpawnLedger();var worlds=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());var start=new CountDownLatch(1);var errors=new AtomicReference<Throwable>();var accepted=new AtomicInteger();var counts=new ConcurrentHashMap<UUID,AtomicInteger>();var threads=new ArrayList<Thread>();
  for(int i=0;i<32;i++){final int n=i;Thread thread=new Thread(()->{try{start.await();UUID w=worlds.get(n%4);var spot=new SpawnLedger.Position(w,n*1000,70,0);UUID reservation=UUID.randomUUID();if(concurrent.reserve(reservation,UUID.randomUUID(),spot,0,limits,false)){accepted.incrementAndGet();counts.computeIfAbsent(w,k->new AtomicInteger()).incrementAndGet();concurrent.commit(reservation,UUID.randomUUID(),spot,0,limits);}}catch(Throwable ex){errors.set(ex);}});threads.add(thread);thread.start();}
  start.countDown();for(Thread thread:threads)thread.join();ok(errors.get()==null);ok(accepted.get()<=6&&concurrent.count()==accepted.get());for(var count:counts.values())ok(count.get()<=2);ok(concurrent.pendingCount()==0);
  ok(RpgTheme.forGroup("snow")==RpgTheme.FROST);ok(RpgTheme.forGroup("taiga")==RpgTheme.FROST);ok(RpgTheme.forGroup("deep-dark")==RpgTheme.CAVE);ok(RpgTheme.forGroup("ocean")==RpgTheme.OCEAN);ok(RpgTheme.forGroup("nether")==RpgTheme.NETHER);ok(RpgTheme.forGroup("end")==null);ok(RpgTheme.find("RPG_FrostWarden")==RpgTheme.FROST);ok(RpgTheme.find("nonsense")==null);
  for(String biome:java.nio.file.Files.readAllLines(java.nio.file.Path.of("tests/biomes.txt"))){String group=BiomePolicy.group(biome,"NORMAL",80,15,false,48);ok(RpgTheme.forGroup(group)!=null);}
  System.out.println("PASS: "+checks+" spawn reservations, parallel caps, cooldown persistence, negative cells, and biome-theme checks");
 }
}
