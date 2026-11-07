package io.duckmc.ui.model;

public final class PerformanceStyle {
   private PerformanceStyle() {
   }

   public static int outlineAt(int localX, int width) {
      return green((double)localX / Math.max(1, width - 1));
   }

   public static int green(double position) {
      double t = Math.max(0.0, Math.min(1.0, position));
      return 0xFF000000 | (int)Math.round(114.0 - 92.0 * t) << 16 | (int)Math.round(235.0 - 150.0 * t) << 8 | (int)Math.round(154.0 - 99.0 * t);
   }
}
