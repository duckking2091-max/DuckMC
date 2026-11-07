package io.duckmc.advantages;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class OwnCrystalPlacement {
   private Set<UUID> existing;
   private long deadline;

   public void arm(Set<UUID> existing, long tick) {
      this.existing = Set.copyOf(existing);
      this.deadline = tick + 20L;
   }

   public Optional<UUID> take(Set<UUID> candidates, long tick) {
      if (this.existing == null) {
         return Optional.empty();
      }

      if (tick >= this.deadline) {
         this.clear();
         return Optional.empty();
      }

      List<UUID> added = candidates.stream().filter(id -> !this.existing.contains(id)).toList();
      if (added.isEmpty()) {
         return Optional.empty();
      }

      this.clear();
      return added.size() == 1 ? Optional.of(added.getFirst()) : Optional.empty();
   }

   public void clear() {
      this.existing = null;
   }

   public boolean pending() {
      return this.existing != null;
   }
}
