package io.duckmc.ui.model;

import java.util.List;

public record SettingsHubLayout(List<HomeLayout.Bounds> buttons) {
   private static final int TOP = 40;
   private static final int GAP = 8;

   public SettingsHubLayout {
      buttons = List.copyOf(buttons);
   }

   public static SettingsHubLayout fit(int x, int width, int height) {
      int cardWidth = (width - 8) / 2;
      int cardHeight = (height - 34 - 40 - 8) / 2;
      int second = x + cardWidth + 8;
      int lower = 40 + cardHeight + 8;
      return new SettingsHubLayout(
         List.of(
            new HomeLayout.Bounds(x, height - 28, 80, 20),
            new HomeLayout.Bounds(x, 40, cardWidth, cardHeight),
            new HomeLayout.Bounds(second, 40, cardWidth, cardHeight),
            new HomeLayout.Bounds(x, lower, cardWidth, cardHeight),
            new HomeLayout.Bounds(second, lower, cardWidth, cardHeight)
         )
      );
   }
}
