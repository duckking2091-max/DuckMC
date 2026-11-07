package io.duckmc.pvp;

public final class ShieldTint {
   private static final ThreadLocal<Integer> CURRENT = ThreadLocal.withInitial(() -> -1);

   private ShieldTint() {
   }

   public static int current() {
      return CURRENT.get();
   }

   public static void withTint(int color, Runnable action) {
      int previous = CURRENT.get();
      CURRENT.set(color);

      try {
         action.run();
      } finally {
         CURRENT.set(previous);
      }
   }

   public static int multiply(int original, int tint) {
      int result = 0;

      for (int shift = 0; shift <= 24; shift += 8) {
         result |= (original >>> shift & 0xFF) * (tint >>> shift & 0xFF) / 255 << shift;
      }

      return result;
   }
}
