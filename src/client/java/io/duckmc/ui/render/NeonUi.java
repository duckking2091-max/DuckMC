package io.duckmc.ui.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public final class NeonUi {
   public static final int GREEN = -13703542;
   public static final int RED = -50340;
   public static final int BLUE = -13653761;
   public static final int GOLD = -15058;
   private static final int NAVY = -16117216;

   private NeonUi() {
   }

   public static int alpha(int color, int alpha) {
      return color & 16777215 | alpha << 24;
   }

   public static int mix(int from, int to, double amount) {
      int color = 0;

      for (int shift = 0; shift <= 24; shift += 8) {
         int a = from >>> shift & 0xFF;
         int b = to >>> shift & 0xFF;
         color |= (int)Math.round(a + (b - a) * amount) << shift;
      }

      return color;
   }

   public static float ease(float value, boolean target, float delta) {
      float step = Math.min(1.0F, Math.max(0.05F, delta) * 0.45F);
      return value + ((target ? 1 : 0) - value) * step;
   }

   public static void shadow(GuiGraphics draw, int x, int y, int w, int h) {
      for (int i = 6; i >= 1; i--) {
         draw.fill(x - i, y - i + 1, x + w + i, y + h + i + 1, 234881024);
      }
   }

   public static void outline(GuiGraphics draw, int x, int y, int w, int h, int color) {
      draw.fill(x + 2, y, x + w - 2, y + 1, color);
      draw.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
      draw.fill(x, y + 2, x + 1, y + h - 2, color);
      draw.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
      draw.fill(x + 1, y + 1, x + 2, y + 2, color);
      draw.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
      draw.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
      draw.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
   }

   public static void glow(GuiGraphics draw, int x, int y, int w, int h, int color, float power) {
      if (!(power <= 0.01F)) {
         outline(draw, x - 1, y - 1, w + 2, h + 2, alpha(color, (int)(90.0F * power)));
         outline(draw, x - 2, y - 2, w + 4, h + 4, alpha(color, (int)(42.0F * power)));
      }
   }

   public static void corners(GuiGraphics draw, int x, int y, int w, int h, int color) {
      int r = x + w;
      int b = y + h;
      int arm = 6;
      draw.fill(x, y, x + arm, y + 2, color);
      draw.fill(x, y, x + 2, y + arm, color);
      draw.fill(r - arm, y, r, y + 2, color);
      draw.fill(r - 2, y, r, y + arm, color);
      draw.fill(x, b - 2, x + arm, b, color);
      draw.fill(x, b - arm, x + 2, b, color);
      draw.fill(r - arm, b - 2, r, b, color);
      draw.fill(r - 2, b - arm, r, b, color);
      draw.fill(x + 3, y + 3, x + 4, y + 4, -520093697);
      draw.fill(r - 4, y + 3, r - 3, y + 4, -520093697);
      draw.fill(x + 3, b - 4, x + 4, b - 3, -520093697);
      draw.fill(r - 4, b - 4, r - 3, b - 3, -520093697);
   }

   public static void panel(GuiGraphics draw, int x, int y, int w, int h, int accent) {
      shadow(draw, x, y, w, h);
      draw.fill(x + 1, y + 1, x + w - 1, y + h - 1, -1073081824);
      draw.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, alpha(accent, 22), 1610612736);
      outline(draw, x, y, w, h, alpha(accent, 200));
      glow(draw, x, y, w, h, accent, 0.55F);
      corners(draw, x, y, w, h, accent);
   }

   public static void panel(GuiGraphics draw, int x, int y, int w, int h, int accent, int background, int gradientEnd, double opacity) {
      shadow(draw, x, y, w, h);
      int alpha = (int)Math.round(255.0 * opacity);
      draw.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, alpha(background, alpha), alpha(gradientEnd, alpha));
      outline(draw, x, y, w, h, alpha(accent, 200));
      glow(draw, x, y, w, h, accent, 0.55F);
      corners(draw, x, y, w, h, accent);
   }

   public static void row(GuiGraphics draw, int x, int y, int w, int h, int accent, float hover) {
      int base = alpha(mix(-16117216, accent, 0.1), 200);
      int lit = alpha(mix(-16117216, accent, 0.22), 225);
      draw.fill(x + 1, y + 1, x + w - 1, y + h - 1, mix(base, lit, hover));
      outline(draw, x, y, w, h, alpha(accent, 70 + (int)(110.0F * hover)));
      draw.fill(x, y + 3, x + 2, y + h - 3, alpha(accent, 170 + (int)(85.0F * hover)));
   }

   public static void inset(GuiGraphics draw, int x, int y, int w, int h, int accent) {
      draw.fill(x + 1, y + 1, x + w - 1, y + h - 1, -1610282478);
      outline(draw, x, y, w, h, alpha(accent, 95));
   }

   public static void bevel(GuiGraphics draw, int x, int y, int w, int h, int accent, float hover, boolean selected, boolean enabled) {
      double strength = !enabled && !selected ? 0.08 : (selected ? 0.72 : 0.26 + 0.22 * hover);
      int top = alpha(mix(-16117216, accent, strength + 0.1), 240);
      int bottom = alpha(mix(-16117216, accent, strength * 0.55), 240);
      draw.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
      draw.fill(x + 2, y + 1, x + w - 2, y + 2, 1174405119);
      draw.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 1342177280);
      int edge = !enabled && !selected ? mix(-13024948, accent, 0.15) : accent;
      outline(draw, x, y, w, h, alpha(edge, selected ? 255 : 150 + (int)(105.0F * hover)));
      if (enabled || selected) {
         glow(draw, x, y, w, h, accent, selected ? 0.7F : hover * 0.8F);
      }

      if (selected) {
         corners(draw, x, y, w, h, mix(accent, -1, 0.45));
      }
   }

   public static void chase(GuiGraphics draw, int x, int y, int w, int h, int color, long millis) {
      int perimeter = 2 * (w + h);
      int length = Math.max(18, perimeter / 8);
      int head = (int)(millis / 4L % perimeter);

      for (int i = 0; i < length; i += 2) {
         int p = Math.floorMod(head - i, perimeter);
         int px;
         int py;
         if (p < w) {
            px = x + p;
            py = y;
         } else if (p < w + h) {
            px = x + w - 1;
            py = y + p - w;
         } else if (p < 2 * w + h) {
            px = x + 2 * w + h - p - 1;
            py = y + h - 1;
         } else {
            px = x;
            py = y + perimeter - p - 1;
         }

         draw.fill(px, py, px + 2, py + 2, alpha(color, 255 - i * 200 / length));
      }
   }
}
