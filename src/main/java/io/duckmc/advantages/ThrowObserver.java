package io.duckmc.advantages;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

public final class ThrowObserver {
   private Object world;
   private final LinkedHashMap<UUID, Long> seen = new LinkedHashMap<>();

   public boolean observe(Object world, UUID projectile, String kind, double distance, double range, long tick) {
      if (this.world != world) {
         this.reset();
         this.world = world;
      }

      this.seen.entrySet().removeIf(e -> tick - e.getValue() > 200L);
      if (world == null
         || projectile == null
         || !Set.of("wind_charge", "ender_pearl").contains(kind)
         || !Double.isFinite(distance)
         || !Double.isFinite(range)
         || distance < 0.0
         || range < 1.0
         || range > 32.0) {
         return false;
      }

      if (this.seen.containsKey(projectile)) {
         return false;
      }

      this.remember(projectile, tick);
      return distance <= range;
   }

   public void prime(Object world, Collection<UUID> current, long tick) {
      this.reset();
      this.world = world;

      for (UUID id : current) {
         if (id != null) {
            this.remember(id, tick);
         }
      }
   }

   private void remember(UUID id, long tick) {
      if (this.seen.size() >= 512 && !this.seen.containsKey(id)) {
         this.seen.remove(this.seen.keySet().iterator().next());
      }

      this.seen.put(id, tick);
   }

   public int size() {
      return this.seen.size();
   }

   public void expire(long tick) {
      this.seen.entrySet().removeIf(e -> tick - e.getValue() > 200L);
   }

   public void reset() {
      this.world = null;
      this.seen.clear();
   }
}
