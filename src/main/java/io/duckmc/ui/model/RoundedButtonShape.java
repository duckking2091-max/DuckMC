package io.duckmc.ui.model;

public final class RoundedButtonShape {
   private RoundedButtonShape() {
   }

   public static int inset(int row, int height) {
      int edge = Math.min(row, height - 1 - row);
      return edge == 0 ? 4 : (edge == 1 ? 2 : (edge < 4 ? 1 : 0));
   }
}
