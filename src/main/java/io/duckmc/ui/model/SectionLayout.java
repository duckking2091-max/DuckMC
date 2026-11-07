package io.duckmc.ui.model;

public record SectionLayout(double scale, int contentWidth, int height) {
   public static final int SIDEBAR_WIDTH = 112;

   public static SectionLayout fit(int width, int height, double requested) {
      double scale = Math.min(UiLayout.scale(width, height, requested), width / 444.0);
      return new SectionLayout(scale, (int)Math.floor(width / scale) - 112, (int)Math.floor(height / scale));
   }
}
