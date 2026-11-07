package io.duckmc.advantages;

import java.util.function.Supplier;

public final class InputGuard {
   private boolean generated;
   private boolean useHeld;

   public boolean generated() {
      return this.generated;
   }

   public <T> T generate(Supplier<T> action) {
      boolean previous = this.generated;
      this.generated = true;

      try {
         return action.get();
      } finally {
         this.generated = previous;
      }
   }

   public boolean freshUse() {
      if (this.useHeld) {
         return false;
      }

      this.useHeld = true;
      return true;
   }

   public void freshPress() {
      this.useHeld = false;
   }

   public void observeUseKey(boolean pressed) {
      if (!pressed) {
         this.useHeld = false;
      }
   }
}
