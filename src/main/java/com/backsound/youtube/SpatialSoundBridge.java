package com.backsound.youtube;

import java.lang.reflect.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Client-only empty text display carries the moving sound source. Attenuation
 * changes the already-playing sound without restarting it. No server entity,
 * chunk loading, additional plugin or audio transcoding is involved.
 * Packet layout targets Mojang-mapped Paper/Folia 1.21.11.
 */
final class SpatialSoundBridge {
    private static final AtomicInteger IDS = new AtomicInteger(-1_500_000_000);
    private final Consumer<String> warning;
    private volatile Backend backend;
    private volatile boolean failed;
    SpatialSoundBridge(Consumer<String> warning) { this.warning = warning; }
    private synchronized void fail(Throwable error) {
        if (failed) return;
        failed = true;
        if (error instanceof InvocationTargetException invocation && invocation.getCause() != null) error = invocation.getCause();
        warning.accept("Fade audio tidak tersedia pada runtime ini; memakai pemutar biasa: " + error.getClass().getSimpleName() + ": " + error.getMessage());
    }
    synchronized Channel open(Player player, Location location) {
        if (failed) return null;
        try {
            if (backend == null) backend = new Backend();
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("connection").get(handle);
            Method send = connection.getClass().getMethod("send", backend.packet);
            Channel channel = new Channel(backend, handle, connection, send, location.getWorld().getUID(), IDS.getAndIncrement());
            channel.send(backend.spawn.newInstance(channel.id, UUID.randomUUID(), location.getX(), location.getY(), location.getZ(), 0f, 0f, backend.displayType, 0, backend.zero, 0d));
            return channel;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) { fail(ex); return null; }
    }
    final class Channel {
        final Backend b;
        final Object handle, connection;
        final Method sender;
        final UUID world;
        final int id;
        final AtomicBoolean closed = new AtomicBoolean();
        double x = Double.NaN, y, z;
        Channel(Backend b, Object handle, Object connection, Method sender, UUID world, int id) {
            this.b=b; this.handle=handle; this.connection=connection; this.sender=sender; this.world=world; this.id=id;
        }
        private void send(Object packet) throws ReflectiveOperationException { sender.invoke(connection, packet); }
        boolean play(String key, float volume) {
            if (closed.get() || failed) return false;
            try {
                Object sound = b.variableSound.invoke(null, b.parse.invoke(null, key));
                Object holder = b.direct.invoke(null, sound);
                Object packet = b.sound.newInstance(holder, b.records, handle, volume, 1f, java.util.concurrent.ThreadLocalRandom.current().nextLong());
                // This is a normal packet class, not a record. Its vanilla
                // constructor accepts an Entity; replace only the serialized
                // source id with the client-only entity id before sending.
                b.soundId.setInt(packet, id);
                send(packet);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ex) { fail(ex); return false; }
        }
        boolean move(Location position) {
            if (closed.get() || failed) return false;
            if (Double.isFinite(x) && Math.abs(x-position.getX()) < .01 && Math.abs(y-position.getY()) < .01 && Math.abs(z-position.getZ()) < .01) return true;
            try {
                Object vector = b.vector.newInstance(position.getX(), position.getY(), position.getZ());
                Object values = b.position.newInstance(vector, b.zero, 0f, 0f);
                send(b.move.newInstance(id, values, false));
                x=position.getX(); y=position.getY(); z=position.getZ();
                return true;
            } catch (ReflectiveOperationException | RuntimeException ex) { fail(ex); return false; }
        }
        void close() {
            if (!closed.compareAndSet(false, true)) return;
            try { send(b.remove.newInstance((Object)new int[]{id})); }
            catch (ReflectiveOperationException | RuntimeException ignored) { /* disconnected */ }
        }
    }
    private static final class Backend {
        final Class<?> packet;
        final Constructor<?> vector, position, spawn, move, remove, sound;
        final Method parse, variableSound, direct;
        final Field soundId;
        final Object zero, displayType, records;
        @SuppressWarnings({"unchecked", "rawtypes"})
        Backend() throws ReflectiveOperationException {
            packet=Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> vec=Class.forName("net.minecraft.world.phys.Vec3");
            Class<?> pos=Class.forName("net.minecraft.world.entity.PositionMoveRotation");
            Class<?> type=Class.forName("net.minecraft.world.entity.EntityType");
            Class<?> entity=Class.forName("net.minecraft.world.entity.Entity");
            Class<?> source=Class.forName("net.minecraft.sounds.SoundSource");
            Class<?> holder=Class.forName("net.minecraft.core.Holder");
            Class<?> soundEvent=Class.forName("net.minecraft.sounds.SoundEvent");
            Class<?> identifier;
            try { identifier=Class.forName("net.minecraft.resources.Identifier"); }
            catch (ClassNotFoundException old) { identifier=Class.forName("net.minecraft.resources.ResourceLocation"); }
            vector=vec.getConstructor(double.class,double.class,double.class);
            zero=vector.newInstance(0d,0d,0d);
            position=pos.getConstructor(vec,vec,float.class,float.class);
            spawn=Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket").getConstructor(int.class,UUID.class,double.class,double.class,double.class,float.class,float.class,type,int.class,vec,double.class);
            move=Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket").getConstructor(int.class,pos,boolean.class);
            remove=Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket").getConstructor(int[].class);
            Class<?> soundPacket=Class.forName("net.minecraft.network.protocol.game.ClientboundSoundEntityPacket");
            sound=soundPacket.getConstructor(holder,source,entity,float.class,float.class,long.class);
            soundId=soundPacket.getDeclaredField("id"); soundId.setAccessible(true);
            parse=identifier.getMethod("parse",String.class);
            variableSound=soundEvent.getMethod("createVariableRangeEvent",identifier);
            direct=holder.getMethod("direct",Object.class);
            displayType=type.getField("TEXT_DISPLAY").get(null);
            records=Enum.valueOf((Class)source,"RECORDS");
        }
    }
}
