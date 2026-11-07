package io.duckmc.ui.model;

public record HudPlacement(double x, double y, double scale) {
   public HudPlacement(double x, double y, double scale) {
      if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(scale) && !(scale < 0.5) && !(scale > 3.0)) {
         this.x = x;
         this.y = y;
         this.scale = scale;
      } else {
         throw new IllegalArgumentException("Invalid HUD placement");
      }
   }

   public HudPlacement clamp(int viewportWidth, int viewportHeight, int cardWidth, int cardHeight) {
      if (viewportWidth > 0 && viewportHeight > 0 && cardWidth >= 0 && cardHeight >= 0) {
         return new HudPlacement(
            Math.max(0.0, Math.min(this.x, 1.0 - cardWidth * this.scale / viewportWidth)),
            Math.max(0.0, Math.min(this.y, 1.0 - cardHeight * this.scale / viewportHeight)),
            this.scale
         );
      } else {
         throw new IllegalArgumentException("Invalid viewport");
      }
   }
}
