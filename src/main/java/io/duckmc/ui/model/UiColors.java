package io.duckmc.ui.model;

public final class UiColors {
   private UiColors() {
   }

   public static int whiteGold(double position) {
      double t = Math.max(0.0, Math.min(1.0, position));
      int green = (int)Math.round(255.0 - 43.0 * t);
      int blue = (int)Math.round(255.0 - 184.0 * t);
      return -65536 | green << 8 | blue;
   }

   public static int darker(int argb) {
      return argb & 0xFF000000 | (argb & 16711422) >>> 1;
   }
}
