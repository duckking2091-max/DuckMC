package io.duckmc.ui.model;

public enum SectionPalette {
   GREEN(-11290500, -15315918, true),
   RED(-1686956, -9956314, true),
   GOLD(-8379, -5673966, true),
   BLUE_TAB(-8923137, -15250538, true),
   BLUE(-8923137, -14450995, false),
   WHITE_GOLD(-1, -11193, false);

   private final int start;
   private final int end;
   public final boolean horizontal;

   SectionPalette(int start, int end, boolean horizontal) {
      this.start = start;
      this.end = end;
      this.horizontal = horizontal;
   }

   public int colorAt(int x, int y, int width, int height) {
      double t = this.horizontal ? (double)x / Math.max(1, width - 1) : (double)y / Math.max(1, height - 1);
      t = Math.max(0.0, Math.min(1.0, t));
      int color = -16777216;

      for (int shift = 0; shift <= 16; shift += 8) {
         int a = this.start >>> shift & 0xFF;
         int b = this.end >>> shift & 0xFF;
         color |= (int)Math.round(a + (b - a) * t) << shift;
      }

      return color;
   }
}
