package io.duckmc.pvp;

import java.util.List;

public final class CrystalLookup {
   private List<Boolean> previous = List.of();
   private int slot = -1;

   public int slot(List<Boolean> hotbar) {
      if (hotbar.size() != 9) {
         throw new IllegalArgumentException("Expected nine hotbar slots");
      }

      List<Boolean> snapshot = List.copyOf(hotbar);
      if (!snapshot.equals(this.previous)) {
         this.previous = snapshot;
         this.slot = snapshot.indexOf(true);
      }

      return this.slot;
   }
}
