package com.backsound.youtube;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.*;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;

final class RpgBossController implements Listener {
 private final YouTubeBacksound plugin;
 private final SpawnLedger ledger=new SpawnLedger();
 private final ConcurrentMap<UUID,Long> joined=new ConcurrentHashMap<>();
 private final ConcurrentMap<UUID,Tracked> tracked=new ConcurrentHashMap<>();
 private final NamespacedKey optout,itemKey;
 private volatile Config config;
 private ScheduledTask monitor;
 private volatile boolean closed;
 private int cursor;
 private final Object saveLock=new Object();
 private record Config(boolean enabled,long interval,double chance,long session,double minDistance,double maxDistance,double safeRadius,SpawnLedger.Limits limits,long lifetime,double weaponChance,Set<String> allowed,Set<String> excluded,Set<String> themes){}
 private static final class Tracked {final RpgTheme theme;volatile Location location;volatile long expires;volatile ScheduledTask task;Tracked(RpgTheme theme){this.theme=theme;}}
 RpgBossController(YouTubeBacksound plugin){this.plugin=plugin;optout=new NamespacedKey(plugin,"rpgboss_optout");itemKey=new NamespacedKey(plugin,"rpgboss_item");}
 private Config readConfig()throws Exception {
  YamlConfiguration y=new YamlConfiguration();y.load(plugin.getDataFolder().toPath().resolve("boss-spawns.yml").toFile());
  int global=bounded(y.getInt("maximum-global",6),1,32),perWorld=bounded(y.getInt("maximum-per-world",2),1,32);
  double min=number(y.getDouble("minimum-distance",24),16,96),max=number(y.getDouble("maximum-distance",40),min,128);
  var limits=new SpawnLedger.Limits(global,perWorld,number(y.getDouble("minimum-boss-spacing",192),32,1024),bounded(y.getInt("area-cell-size",256),32,2048),(long)number(y.getDouble("area-cooldown-seconds",1800),60,86400)*1000,(long)number(y.getDouble("player-cooldown-seconds",1200),60,86400)*1000);
  return new Config(y.getBoolean("enabled",true),(long)number(y.getDouble("interval-seconds",60),10,3600)*20,number(y.getDouble("spawn-chance",.2),0,1),(long)number(y.getDouble("minimum-session-seconds",300),0,3600)*1000,min,max,number(y.getDouble("world-spawn-safe-radius",256),0,4096),limits,(long)number(y.getDouble("boss-lifetime-seconds",1800),60,86400)*1000,number(y.getDouble("weapon-drop-chance",.2),0,1),strings(y,"allowed-worlds"),strings(y,"excluded-worlds"),strings(y,"enabled-themes"));
 }
 private static Set<String> strings(YamlConfiguration y,String path){return y.getStringList(path).stream().map(s->s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());}
 private static int bounded(int v,int min,int max){if(v<min||v>max)throw new IllegalArgumentException("Batas spawn tidak valid");return v;}
 private static double number(double v,double min,double max){if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("Angka konfigurasi spawn tidak valid: "+v);return v;}
 void start()throws Exception {
  config=readConfig();loadState();plugin.getServer().getPluginManager().registerEvents(this,plugin);
  long now=System.currentTimeMillis();for(Player p:plugin.getServer().getOnlinePlayers())joined.put(p.getUniqueId(),now);
  rescan();schedule();
  plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,t->{if(!closed)saveState();},1,1,TimeUnit.MINUTES);
 }
 void rescan(){for(var active:MythicBukkit.inst().getMobManager().getActiveMobs()){var theme=RpgTheme.find(active.getType().getInternalName());if(theme!=null && active.getEntity()!=null && active.getEntity().getBukkitEntity()!=null)watch(active.getEntity().getBukkitEntity(),theme);}}
 private void schedule(){if(monitor!=null)monitor.cancel();monitor=plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,t->checkPlayer(),config.interval,config.interval);}
 private void checkPlayer(){if(closed||!config.enabled)return;var players=new ArrayList<>(plugin.getServer().getOnlinePlayers());if(players.isEmpty())return;Player p=players.get(Math.floorMod(cursor++,players.size()));p.getScheduler().run(plugin,t->{if(!closed)attempt(p,null,false);},null);}
 @EventHandler public void join(PlayerJoinEvent e){joined.put(e.getPlayer().getUniqueId(),System.currentTimeMillis());}
 @EventHandler public void quit(PlayerQuitEvent e){joined.remove(e.getPlayer().getUniqueId());}
 private boolean eligible(Player p,Config c){
  if(!p.isOnline()||p.isDead()||(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE))return false;
  if(p.getPersistentDataContainer().has(optout,PersistentDataType.BYTE))return false;
  if(System.currentTimeMillis()-joined.getOrDefault(p.getUniqueId(),System.currentTimeMillis())<c.session)return false;
  String world=p.getWorld().getName().toLowerCase(Locale.ROOT);if(c.excluded.contains(world)||(!c.allowed.isEmpty()&&!c.allowed.contains(world)))return false;
  var spawn=p.getWorld().getSpawnLocation();var loc=p.getLocation();double dx=spawn.getX()-loc.getX(),dz=spawn.getZ()-loc.getZ();return dx*dx+dz*dz>=c.safeRadius*c.safeRadius;
 }
 private void attempt(Player p,RpgTheme requested,boolean manual){
  Config c=config;if(!manual&&(!eligible(p,c)||ThreadLocalRandom.current().nextDouble()>=c.chance))return;
  var origin=p.getLocation();String group=BiomePolicy.group(origin.getBlock().getBiome().getKey().getKey(),p.getWorld().getEnvironment().name(),origin.getBlockY(),0,false,plugin.caveDepth());
  RpgTheme theme=requested!=null?requested:RpgTheme.forGroup(group);if(theme==null||(!manual&&!c.themes.contains(theme.key)))return;
  if(MythicBukkit.inst().getMobManager().getMythicMob(theme.id).isEmpty()){if(manual)reply(p,"Definisi boss belum dimuat; jalankan /mm reload.");return;}
  double angle=ThreadLocalRandom.current().nextDouble(Math.PI*2),distance=ThreadLocalRandom.current().nextDouble(c.minDistance,c.maxDistance+.0001);
  Location candidate=new Location(origin.getWorld(),Math.floor(origin.getX()+Math.cos(angle)*distance)+.5,origin.getY(),Math.floor(origin.getZ()+Math.sin(angle)*distance)+.5);
  UUID ticket=UUID.randomUUID();if(!ledger.reserve(ticket,p.getUniqueId(),position(candidate),System.currentTimeMillis(),c.limits,manual)){if(manual)reply(p,"Batas boss, jarak antar-boss, atau cooldown belum terpenuhi.");return;}
  if(!candidate.getWorld().isChunkLoaded(candidate.getBlockX()>>4,candidate.getBlockZ()>>4)){ledger.release(ticket);if(manual)reply(p,"Area tujuan belum dimuat; coba di area terbuka lain.");return;}
  UUID owner=p.getUniqueId();plugin.getServer().getRegionScheduler().run(plugin,candidate,task->{
   boolean committed=false;try{
    if(closed)return;Location safe=safeLocation(candidate,theme,group);if(safe==null){if(manual)reply(p,"Tidak menemukan lokasi spawn dengan ruang cukup.");return;}
    if(closed)return;
    var active=MythicBukkit.inst().getMobManager().spawnMob(theme.id,safe,1d);if(active==null||active.getEntity()==null)return;
    Entity entity=active.getEntity().getBukkitEntity();if(entity==null||!entity.isValid())return;if(closed){entity.remove();return;}
    committed=ledger.commit(ticket,entity.getUniqueId(),position(safe),System.currentTimeMillis(),c.limits);
    if(!committed){entity.remove();return;}
    if(closed){entity.remove();ledger.remove(entity.getUniqueId());return;}
    watch(entity,theme);Tracked data=tracked.get(entity.getUniqueId());if(data!=null)data.expires=System.currentTimeMillis()+c.lifetime;
    reply(p,"⚔ "+theme.title+" muncul di dekatmu! ("+safe.getBlockX()+", "+safe.getBlockY()+", "+safe.getBlockZ()+")");
   }catch(Exception ex){plugin.getLogger().warning("Spawn boss gagal: "+ex.getMessage());if(manual)reply(p,"Spawn boss gagal; periksa console.");}
   finally{if(!committed)ledger.release(ticket);}
  });
 }
 private Location safeLocation(Location at,RpgTheme theme,String group){
  World world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ();if(!world.isChunkLoaded(x>>4,z>>4))return null;
  if(!world.getWorldBorder().isInside(at))return null;
  if(theme==RpgTheme.OCEAN){for(int d=0;d<10;d++){int y=at.getBlockY()-d;if(y<=world.getMinHeight()||y+3>=world.getMaxHeight())continue;if(world.getBlockAt(x,y,z).getType()==Material.WATER&&world.getBlockAt(x,y+1,z).getType()==Material.WATER&&world.getBlockAt(x,y+2,z).getType()==Material.WATER)return new Location(world,x+.5,y,z+.5);}}
  boolean underground=Set.of("cave","deep-dark","lush-caves","nether").contains(group)||theme==RpgTheme.CAVE||world.getEnvironment()==World.Environment.NETHER;
  int middle=underground?at.getBlockY():world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
  for(int i=0;i<19;i++){int y=middle+(i==0?0:(i%2==1?(i+1)/2:-i/2));if(y<=world.getMinHeight()||y+5>=world.getMaxHeight())continue;
   var floor=world.getBlockAt(x,y-1,z);Material material=floor.getType();if(!material.isSolid()||Tag.LEAVES.isTagged(material)||Set.of(Material.BEDROCK,Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW).contains(material))continue;
   boolean clear=true;for(int h=0;h<5;h++){var b=world.getBlockAt(x,y+h,z);if(!b.isPassable()||b.isLiquid()||b.getType()==Material.FIRE||b.getType()==Material.SOUL_FIRE){clear=false;break;}}
   if(clear)return new Location(world,x+.5,y,z+.5);
  }return null;
 }
 private static SpawnLedger.Position position(Location l){return new SpawnLedger.Position(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void spawn(MythicMobSpawnEvent e){RpgTheme theme=RpgTheme.find(e.getMobType().getInternalName());if(theme!=null)watch(e.getEntity(),theme);}
 private void watch(Entity entity,RpgTheme theme){
  if(closed)return;
  UUID id=entity.getUniqueId();Tracked data=new Tracked(theme);if(tracked.putIfAbsent(id,data)!=null)return;
  if(plugin.getServer().isOwnedByCurrentRegion(entity)){data.location=entity.getLocation();ledger.track(id,position(data.location));}
  data.task=entity.getScheduler().runAtFixedRate(plugin,task->{
   if(closed||!entity.isValid()||entity.isDead()){remove(id);task.cancel();return;}
   data.location=entity.getLocation();ledger.track(id,position(data.location));
   if(entity instanceof LivingEntity living && living.getEquipment()!=null){var helmet=living.getEquipment().getHelmet();if(helmet==null||helmet.getType().isAir()||!helmet.hasItemMeta()||!helmet.getItemMeta().getPersistentDataContainer().has(itemKey,PersistentDataType.STRING)){living.getEquipment().setHelmet(crown(theme));living.getEquipment().setHelmetDropChance(0);living.getEquipment().setItemInMainHand(weapon(theme));living.getEquipment().setItemInMainHandDropChance(0);}}
   if(data.expires>0&&System.currentTimeMillis()>data.expires){if(entity instanceof Mob mob && mob.getTarget()!=null)data.expires=System.currentTimeMillis()+60_000;else{entity.remove();remove(id);task.cancel();}}
  },()->{if(tracked.remove(id,data))ledger.remove(id);},1,100);
  if(data.task==null||closed)remove(id);
 }
 @EventHandler(priority=EventPriority.MONITOR)public void despawn(MythicMobDespawnEvent e){remove(e.getEntity().getUniqueId());}
 @EventHandler(priority=EventPriority.MONITOR)public void death(MythicMobDeathEvent e){
  RpgTheme theme=RpgTheme.find(e.getMobType().getInternalName());if(theme==null)return;Tracked data=tracked.get(e.getEntity().getUniqueId());
  Location location=plugin.getServer().isOwnedByCurrentRegion(e.getEntity())?e.getEntity().getLocation():(data==null?null:data.location);
  remove(e.getEntity().getUniqueId());if(location==null||closed)return;Location spot=location.clone();
  plugin.getServer().getRegionScheduler().run(plugin,spot,t->{if(closed||!spot.getWorld().isChunkLoaded(spot.getBlockX()>>4,spot.getBlockZ()>>4))return;
   var drops=new ArrayList<ItemStack>();var table=plugin.getServer().getLootTable(NamespacedKey.fromString("rpgboss:bosses/"+theme.key));
   if(table!=null)try{drops.addAll(table.populateLoot(new Random(),new org.bukkit.loot.LootContext.Builder(spot).build()));}catch(RuntimeException ignored){}
   if(drops.isEmpty()){drops.add(new ItemStack(Material.EMERALD,ThreadLocalRandom.current().nextInt(4,9)));if(ThreadLocalRandom.current().nextDouble()<.25)drops.add(new ItemStack(Material.DIAMOND));}
   drops.add(sigil(theme));if(ThreadLocalRandom.current().nextDouble()<config.weaponChance)drops.add(weapon(theme));
   for(ItemStack item:drops)spot.getWorld().dropItemNaturally(spot,item);
  });
 }
 private void remove(UUID id){Tracked data=tracked.remove(id);if(data!=null&&data.task!=null)data.task.cancel();ledger.remove(id);}
 private ItemStack item(Material material,RpgTheme theme,String suffix,String name){var stack=new ItemStack(material);var meta=stack.getItemMeta();meta.setItemModel(new NamespacedKey("rpgboss",theme.key+"_"+suffix));meta.displayName(Component.text(name,TextColor.color(theme.color)).decoration(TextDecoration.ITALIC,false));meta.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,theme.key+"_"+suffix);meta.lore(List.of(Component.text("Relik "+theme.title,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));stack.setItemMeta(meta);return stack;}
 private ItemStack crown(RpgTheme theme){var stack=item(Material.PAPER,theme,"crown",theme.title+" Crown");var meta=stack.getItemMeta();var equip=meta.getEquippable();equip.setSlot(EquipmentSlot.HEAD);equip.setModel(null);equip.setCameraOverlay(null);equip.setDamageOnHurt(false);meta.setEquippable(equip);stack.setItemMeta(meta);return stack;}
 private ItemStack weapon(RpgTheme theme){var stack=item(Material.valueOf(theme.material),theme,"weapon",theme.title+" Relic");var meta=stack.getItemMeta();meta.addEnchant(theme==RpgTheme.OCEAN?org.bukkit.enchantments.Enchantment.IMPALING:org.bukkit.enchantments.Enchantment.SHARPNESS,3,true);meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,2,true);stack.setItemMeta(meta);return stack;}
 private ItemStack sigil(RpgTheme theme){return item(Material.AMETHYST_SHARD,theme,"sigil",theme.title+" Sigil");}
 private static final class Menu implements InventoryHolder{Inventory inventory;public Inventory getInventory(){return inventory;}}
 private void menu(Player p){Menu menu=new Menu();menu.inventory=plugin.getServer().createInventory(menu,27,Component.text("RPG Boss Codex",NamedTextColor.GOLD));int slot=10;for(RpgTheme theme:RpgTheme.values()){if(slot==13)slot++;var item=crown(theme);var meta=item.getItemMeta();meta.lore(List.of(Component.text(theme.id,NamedTextColor.GRAY),Component.text("Spawn otomatis sesuai biome",NamedTextColor.GREEN),Component.text("Lagu boss menyusul",NamedTextColor.DARK_GRAY)));item.setItemMeta(meta);menu.inventory.setItem(slot++,item);}p.openInventory(menu.inventory);}
 @EventHandler public void click(InventoryClickEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
 @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
 boolean command(CommandSender sender,String[] args){if(closed)return true;String sub=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
  if(sub.equals("status")){reply(sender,"RPG boss aktif: "+ledger.count()+"; pending: "+ledger.pendingCount()+"; otomatis: "+config.enabled);return true;}
  if(sub.equals("reload")){if(!sender.hasPermission("rpgboss.admin")){reply(sender,"Perlu permission rpgboss.admin.");return true;}plugin.getServer().getGlobalRegionScheduler().execute(plugin,()->{try{Config next=readConfig();config=next;schedule();reply(sender,"Konfigurasi spawn diperbarui.");}catch(Exception ex){reply(sender,"Reload gagal, konfigurasi lama dipertahankan: "+ex.getMessage());}});return true;}
  if(!(sender instanceof Player p)){reply(sender,"Gunakan /rpgboss status atau reload dari console.");return true;}
  if(sub.equals("toggle")){p.getScheduler().run(plugin,t->{var data=p.getPersistentDataContainer();if(data.has(optout,PersistentDataType.BYTE)){data.remove(optout);reply(p,"Spawn boss dekatmu diaktifkan.");}else{data.set(optout,PersistentDataType.BYTE,(byte)1);reply(p,"Spawn boss dekatmu dinonaktifkan.");}},null);return true;}
  if(sub.equals("spawn")){if(!sender.hasPermission("rpgboss.admin")){reply(sender,"Perlu permission rpgboss.admin.");return true;}RpgTheme theme=args.length>1?RpgTheme.find(args[1]):null;if(theme==null){reply(p,"/rpgboss spawn <frost|desert|cave|forest|ocean|nether>");return true;}p.getScheduler().run(plugin,t->{if(!closed)attempt(p,theme,true);},null);return true;}
  p.getScheduler().run(plugin,t->{if(!closed)menu(p);},null);return true;
 }
 private void reply(CommandSender sender,String text){if(closed)return;if(sender instanceof Player p)p.getScheduler().run(plugin,t->{if(!closed)p.sendMessage(text);},null);else sender.sendMessage(text);}
 private void loadState(){var y=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("spawn-state.yml").toFile());var section=y.getConfigurationSection("cooldowns");var values=new HashMap<String,Long>();if(section!=null)for(String key:section.getKeys(false))values.put(key,section.getLong(key));ledger.restore(values,System.currentTimeMillis());}
 private void saveState(){synchronized(saveLock){try{YamlConfiguration y=new YamlConfiguration();ledger.cooldownSnapshot(System.currentTimeMillis()).forEach((key,value)->y.set("cooldowns."+key,value));Path target=plugin.getDataFolder().toPath().resolve("spawn-state.yml"),temp=Files.createTempFile(target.getParent(),"spawn-state-",".tmp");try{y.save(temp.toFile());Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}catch(Exception ex){plugin.getLogger().warning("Gagal menyimpan cooldown boss: "+ex.getMessage());}}}
 void close(){closed=true;if(monitor!=null)monitor.cancel();HandlerList.unregisterAll(this);tracked.values().forEach(d->{if(d.task!=null)d.task.cancel();});saveState();tracked.clear();ledger.clear();}
}
