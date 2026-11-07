package io.duckmc.content;

import java.util.ArrayList;
import java.util.List;

public final class ContentGridLayout {
   private ContentGridLayout() {
   }

   public static List<ContentGridLayout.Rect> cards(int width, int availableHeight, int top, int scroll) {
      int left = Math.max(14, (width - 600) / 2);
      int area = Math.min(600, width - 28);
      int w = Math.max(1, (area - 8) / 2);
      int h = Math.max(32, Math.min(140, (availableHeight - 8) / 2));
      ArrayList<ContentGridLayout.Rect> cards = new ArrayList<>();

      for (int i = 0; i < 4; i++) {
         cards.add(new ContentGridLayout.Rect(left + i % 2 * (w + 8), top + i / 2 * (h + 8) - scroll, w, h));
      }

      return List.copyOf(cards);
   }

   public record Rect(int x, int y, int width, int height) {
   }
}
