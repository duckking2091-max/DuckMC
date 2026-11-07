package io.duckmc.ui.model;

public final class UiLayout {
   private UiLayout() {
   }

   public static double scale(int width, int height, double requested) {
      return Math.max(0.25, Math.min(requested, Math.min(width / 320.0, height / 240.0)));
   }

   public static double reveal(long elapsedNanos, double speed, boolean reducedMotion) {
      return !reducedMotion && speed != 0.0 ? Math.max(0.0, Math.min(1.0, elapsedNanos / 2.5E8 * speed)) : 1.0;
   }
}
