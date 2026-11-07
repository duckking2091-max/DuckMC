package io.duckmc.pvp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TotemPops {
   private Object world;
   private final LinkedHashMap<UUID, TotemPops.Entry> entries = new LinkedHashMap<>();

   private void scope(Object next) {
      if (this.world != next) {
         this.entries.clear();
         this.world = next;
      }
   }

   public void pop(Object world, UUID player, Object entity) {
      this.scope(world);
      int count = this.count(world, player, entity);
      if (!this.entries.containsKey(player) && this.entries.size() >= 128) {
         this.entries.remove(this.entries.keySet().iterator().next());
      }

      this.entries.put(player, new TotemPops.Entry(entity, Math.min(10000, count + 1)));
   }

   public int count(Object world, UUID player, Object entity) {
      this.scope(world);
      TotemPops.Entry entry = this.entries.get(player);
      return entry != null && entry.entity() == entity ? entry.count() : 0;
   }

   public void forget(Object world, UUID player, Object entity) {
      this.scope(world);
      TotemPops.Entry entry = this.entries.get(player);
      if (entry != null && entry.entity() == entity) {
         this.entries.remove(player);
      }
   }

   public void retain(Object world, Map<UUID, ?> alive) {
      this.scope(world);
      this.entries.entrySet().removeIf(e -> alive.get(e.getKey()) != e.getValue().entity());
   }

   public int size() {
      return this.entries.size();
   }

   public void clear() {
      this.entries.clear();
      this.world = null;
   }

   private record Entry(Object entity, int count) {
   }
}
