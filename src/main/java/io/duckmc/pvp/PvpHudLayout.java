package io.duckmc.pvp;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public final class PvpHudLayout {
   private PvpHudLayout() {
   }

   public static PvpHudLayout.Rect target(int width, int height, int lineHeight) {
      int w = Math.min(140, width);
      int h = Math.min(height, Math.max(1, lineHeight) * 2);
      return new PvpHudLayout.Rect((width - w) / 2, Math.min(height - h, height / 2 + 12), w, h);
   }

   public static List<PvpHudLayout.Rect> armor(int width, int height, boolean leftHanded, boolean offhandOccupied, double scale) {
      int boundary = Math.max(1, width / 2 - (!leftHanded && offhandOccupied ? 124 : 96));
      double s = Math.max(0.1, Math.min((Double.isFinite(scale) ? scale : 1.0) * 1.5, Math.min(boundary / 56.0, (height - 8) / 108.0)));
      int w = Math.max(1, (int)(56.0 * s));
      int h = Math.max(1, (int)(18.0 * s));
      ArrayList<PvpHudLayout.Rect> result = new ArrayList<>();
      int x = Math.max(0, boundary - w);

      for (int i = 0; i < 6; i++) {
         result.add(new PvpHudLayout.Rect(x, height - 4 - h * (6 - i), w, h));
      }

      return List.copyOf(result);
   }

   public static PvpHudLayout.Rect totem(int width, int height, int healthRows, int xpTextTop, double scale) {
      double s = Math.clamp(Double.isFinite(scale) ? scale : 1.0, 0.5, 1.0);
      int w = Math.min((int)Math.round(20.0 * s), width);
      int h = (int)Math.round(36.0 * s);
      int y = Math.max(0, Math.min(height - 50 - Math.max(0, healthRows - 1) * 10, xpTextTop - h - 2));
      return new PvpHudLayout.Rect((width - w) / 2, y, w, h);
   }

   public static OptionalInt durabilityPercent(int remaining, int maximum) {
      return maximum > 0 && remaining >= 0 && remaining <= maximum ? OptionalInt.of((int)(remaining * 100L / maximum)) : OptionalInt.empty();
   }

   public static int durabilityColor(int percent) {
      double t = Math.clamp(percent, 0, 100) / 100.0;
      return 0xFF000000 | (int)Math.round(128.0 + -43.0 * t) << 16 | (int)Math.round(255.0 * t) << 8 | (int)Math.round(85.0 * t);
   }

   public static boolean anchored(String id) {
      return Set.of("target_info", "armor_status", "totem_status").contains(id);
   }

   public record Rect(int x, int y, int width, int height) {
   }
}
