package io.duckmc.pvp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ShieldObservations {
   private static final int LIMIT = 128;
   private static final long LIFETIME = 100L;
   private final LinkedHashMap<UUID, ShieldObservations.Evidence> players = new LinkedHashMap<>();
   private Object world;
   private long lastTick = Long.MIN_VALUE;

   private void scope(Object nextWorld, long tick) {
      if (this.world != nextWorld || tick < this.lastTick) {
         this.players.clear();
      }

      this.world = nextWorld;
      this.lastTick = tick;
   }

   private ShieldObservations.Evidence entry(UUID player) {
      ShieldObservations.Evidence entry = this.players.get(player);
      if (entry == null) {
         if (this.players.size() == 128) {
            this.players.remove(this.players.keySet().iterator().next());
         }

         entry = new ShieldObservations.Evidence();
         this.players.put(player, entry);
      }

      return entry;
   }

   public void blocking(Object world, UUID player, long tick) {
      this.scope(world, tick);
      ShieldObservations.Evidence entry = this.entry(player);
      if (tick > entry.disabledTick) {
         entry.state = ShieldObservations.State.READY;
         entry.tick = tick;
         entry.needsRelease = false;
      }
   }

   public void sampleBlocking(Object world, UUID player, boolean blocking, long tick) {
      this.scope(world, tick);
      ShieldObservations.Evidence entry = this.entry(player);
      if (!blocking) {
         entry.needsRelease = false;
      } else if (!entry.needsRelease) {
         if (tick <= entry.disabledTick) {
            entry.needsRelease = true;
         } else {
            this.blocking(world, player, tick);
         }
      }
   }

   public void disabled(Object world, UUID player, long tick) {
      this.scope(world, tick);
      ShieldObservations.Evidence entry = this.entry(player);
      entry.state = ShieldObservations.State.DISABLED;
      entry.tick = tick;
      entry.disabledTick = tick;
      entry.needsRelease = true;
   }

   public ShieldObservations.State state(Object world, UUID player, long tick) {
      this.scope(world, tick);
      ShieldObservations.Evidence entry = this.players.get(player);
      return entry != null && tick - entry.tick <= 100L ? entry.state : ShieldObservations.State.UNKNOWN;
   }

   public ShieldObservations.State held(Object world, UUID player, long tick) {
      return this.state(world, player, tick) == ShieldObservations.State.DISABLED ? ShieldObservations.State.DISABLED : ShieldObservations.State.READY;
   }

   public void retain(Object world, Set<UUID> present, long tick) {
      this.scope(world, tick);
      this.players.keySet().retainAll(present);
   }

   public void clear() {
      this.players.clear();
      this.world = null;
      this.lastTick = Long.MIN_VALUE;
   }

   public int size() {
      return this.players.size();
   }

   public static Optional<UUID> correlate(boolean shieldDisableSound, List<ShieldObservations.SoundCandidate> candidates) {
      if (!shieldDisableSound) {
         return Optional.empty();
      }

      UUID match = null;

      for (ShieldObservations.SoundCandidate candidate : candidates) {
         double distance = candidate.squaredDistance();
         if (Double.isFinite(distance) && !(distance < 0.0) && !(distance > 0.0625)) {
            if (match != null) {
               return Optional.empty();
            }

            if (!candidate.eligible()) {
               return Optional.empty();
            }

            match = candidate.player();
         }
      }

      return Optional.ofNullable(match);
   }

   private static final class Evidence {
      ShieldObservations.State state = ShieldObservations.State.UNKNOWN;
      long tick;
      long disabledTick = Long.MIN_VALUE;
      boolean needsRelease;
   }

   public record SoundCandidate(UUID player, double squaredDistance, boolean eligible) {
   }

   public enum State {
      UNKNOWN,
      READY,
      DISABLED;
   }
}
