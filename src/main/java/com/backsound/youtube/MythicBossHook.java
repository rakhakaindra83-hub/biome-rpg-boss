package com.backsound.youtube;
import java.util.*;
import java.util.concurrent.*;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.*;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.projectiles.ProjectileSource;

/** Reads each boss's live entity only on that entity's scheduler. */
final class MythicBossHook implements Listener {
 private final YouTubeBacksound plugin;
 private final BossCombatTracker tracker;
 private final ConcurrentMap<UUID,Watcher> watchers=new ConcurrentHashMap<>();
 private volatile boolean closed;
 private static final class Watcher { volatile ScheduledTask task; }
 MythicBossHook(YouTubeBacksound plugin,BossCombatTracker tracker) {this.plugin=plugin;this.tracker=tracker;}
 void start() {plugin.getServer().getPluginManager().registerEvents(this,plugin);rescan();}
 void rescan() {
  if(closed)return;
  for(var active:MythicBukkit.inst().getMobManager().getActiveMobs())if(active.getEntity()!=null && active.getEntity().getBukkitEntity()!=null)watch(active.getEntity().getBukkitEntity(),active.getType().getInternalName());
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
 public void spawn(MythicMobSpawnEvent event){watch(event.getEntity(),event.getMobType().getInternalName());}
 @EventHandler(priority=EventPriority.MONITOR)
 public void death(MythicMobDeathEvent event){remove(event.getEntity().getUniqueId());}
 @EventHandler(priority=EventPriority.MONITOR)
 public void despawn(MythicMobDespawnEvent event){remove(event.getEntity().getUniqueId());}
 private void watch(Entity entity,String type) {
  if(closed || !plugin.hasBossProfile(type))return;
  UUID id=entity.getUniqueId();Watcher watcher=new Watcher();if(watchers.putIfAbsent(id,watcher)!=null)return;
  watcher.task=entity.getScheduler().runAtFixedRate(plugin,task->{
   if(closed||!entity.isValid()||entity.isDead()){remove(id);task.cancel();return;}
   var position=entity.getLocation();long now=System.nanoTime();
   tracker.update(id,type,new BossCombatTracker.Position(position.getWorld().getUID(),position.getX(),position.getY(),position.getZ()),now);
   if(entity instanceof Mob mob && mob.getTarget() instanceof Player target)tracker.engage(target.getUniqueId(),id,now);
  },()->{if(watchers.remove(id,watcher))tracker.remove(id);},1L,20L);
  if(watcher.task==null || closed){remove(id);if(watcher.task!=null)watcher.task.cancel();}
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
 public void damage(EntityDamageByEntityEvent event) {
  if(closed || event.getFinalDamage()<=0)return;
  Entity actor=event.getDamager();
  if(actor instanceof Projectile projectile){ProjectileSource shooter=projectile.getShooter();if(shooter instanceof Entity entity)actor=entity;}
  Entity victim=event.getEntity();long now=System.nanoTime();
  if(actor instanceof Player player && tracker.known(victim.getUniqueId()))tracker.engage(player.getUniqueId(),victim.getUniqueId(),now);
  if(victim instanceof Player player && tracker.known(actor.getUniqueId()))tracker.engage(player.getUniqueId(),actor.getUniqueId(),now);
 }
 @EventHandler(priority=EventPriority.MONITOR)
 public void playerDeath(PlayerDeathEvent event){tracker.clearPlayer(event.getEntity().getUniqueId());}
 private void remove(UUID id){Watcher watcher=watchers.remove(id);if(watcher!=null&&watcher.task!=null)watcher.task.cancel();tracker.remove(id);}
 void close(){closed=true;HandlerList.unregisterAll(this);watchers.values().forEach(w->{if(w.task!=null)w.task.cancel();});watchers.clear();tracker.clear();}
}
