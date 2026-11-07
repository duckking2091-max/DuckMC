package io.duckmc.ui.model;

public final class ScrollRows {
   private ScrollRows() {
   }

   public static int move(int first, int delta, int count, int visible) {
      return Math.clamp((long)first + delta, 0, Math.max(0, count - visible));
   }
}
