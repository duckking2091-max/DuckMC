package io.duckmc.ui.model;

public record FrameLayout(double scale, double x, double y, int width, int height) {
   public static final double COVERAGE = 0.7;

   public static FrameLayout fit(int windowWidth, int windowHeight, double requested, int minWidth, int minHeight) {
      double frameWidth = windowWidth * 0.7;
      double frameHeight = windowHeight * 0.7;
      double scale = Math.max(0.25, Math.min(requested, Math.min(frameWidth / minWidth, frameHeight / minHeight)));
      int width = (int)Math.floor(frameWidth / scale + 1.0E-6);
      int height = (int)Math.floor(frameHeight / scale + 1.0E-6);
      return new FrameLayout(scale, (windowWidth - width * scale) / 2.0, (windowHeight - height * scale) / 2.0, width, height);
   }
}
