package io.duckmc.advantages;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

public final class HotbarResolver {
   private HotbarResolver() {
   }

   public static OptionalInt first(IntPredicate usable) {
      for (int slot = 0; slot < 9; slot++) {
         if (usable.test(slot)) {
            return OptionalInt.of(slot);
         }
      }

      return OptionalInt.empty();
   }
}
