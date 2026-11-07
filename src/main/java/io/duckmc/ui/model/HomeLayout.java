package io.duckmc.ui.model;

public record HomeLayout(HomeLayout.Bounds banner, int cardX, int cardRowWidth, int cardY, int cardHeight, int footerY, int gap) {
   private static final int SETTINGS_HEIGHT = 18;

   public static HomeLayout fit(int width, int height) {
      int margin = Math.max(5, height / 40);
      int gap = 5;
      int rowWidth = (int)(width * 0.95);
      int cardX = (width - rowWidth) / 2;
      int available = height - margin * 2 - gap * 2 - 18;
      int cardMinimum = Math.max(76, (int)(available * 0.4));
      int bannerHeight = Math.max(30, Math.min(rowWidth / 3, available - cardMinimum));
      int bannerWidth = bannerHeight * 3;
      int cardY = margin + bannerHeight + gap;
      int cardHeight = available - bannerHeight;
      return new HomeLayout(
         new HomeLayout.Bounds((width - bannerWidth) / 2, margin, bannerWidth, bannerHeight), cardX, rowWidth, cardY, cardHeight, cardY + cardHeight + gap, gap
      );
   }

   public HomeLayout.Bounds settings() {
      return new HomeLayout.Bounds(this.banner.x() + (this.banner.width() - 110) / 2, this.footerY, 110, 18);
   }

   public HomeLayout.Bounds card(int index) {
      if (index >= 0 && index <= 3) {
         int w = (this.cardRowWidth - this.gap * 3) / 4;
         return new HomeLayout.Bounds(this.cardX + index * (w + this.gap), this.cardY, w, this.cardHeight);
      } else {
         throw new IllegalArgumentException("Unknown category");
      }
   }

   public record Bounds(int x, int y, int width, int height) {
   }
}
