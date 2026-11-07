package io.duckmc.ui.render;

import io.duckmc.ui.model.Theme;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public final class GlassRenderer {
   private GlassRenderer() {
   }

   public static void panel(GuiGraphics context, int x, int y, int width, int height, Theme theme, boolean shadow) {
      panel(context, x, y, width, height, theme, shadow, theme.opacity());
   }

   public static void panel(GuiGraphics context, int x, int y, int width, int height, Theme theme, boolean shadow, double opacity) {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(theme, "theme");
      if (width > 0 && height > 0) {
         int radius = Math.max(0, Math.min((int)Math.round(theme.radius()), Math.min(width, height) / 2));
         if (shadow && theme.shadows()) {
            roundedFill(context, x + 2, y + 3, width, height, radius, 1375731712);
         }

         int start = withOpacity(theme.background(), opacity);
         int end = withOpacity(theme.gradientEnd(), opacity);
         roundedGradient(context, x, y, width, height, radius, start, theme.gradient() ? end : start);
         roundedOutline(context, x, y, width, height, radius, 855638015);
      }
   }

   public static void card(GuiGraphics context, int x, int y, int width, int height, int accent, Theme theme, boolean selected, boolean hovered) {
      panel(context, x, y, width, height, theme, selected || hovered);
      int radius = Math.max(1, Math.min((int)Math.round(theme.radius()), height / 2));
      roundedFill(context, x, y, 4, height, Math.min(2, radius), accent);
      if (hovered) {
         roundedOutline(context, x, y, width, height, radius, withAlpha(accent, 176));
      }

      if (selected) {
         roundedOutline(context, x - 1, y - 1, width + 2, height + 2, radius + 1, -419430401);
      }
   }

   public static void pill(GuiGraphics context, int x, int y, int width, int height, int background, int outline) {
      int radius = Math.max(1, height / 2);
      roundedFill(context, x, y, width, height, radius, background);
      roundedOutline(context, x, y, width, height, radius, outline);
   }

   public static void withScissor(GuiGraphics context, int x1, int y1, int x2, int y2, Runnable renderer) {
      context.enableScissor(x1, y1, x2, y2);

      try {
         renderer.run();
      } finally {
         context.disableScissor();
      }
   }

   private static void roundedGradient(GuiGraphics context, int x, int y, int width, int height, int radius, int top, int bottom) {
      if (radius <= 1) {
         context.fillGradient(x, y, x + width, y + height, top, bottom);
      } else {
         int inset = Math.min(radius, Math.max(1, width / 4));
         context.fillGradient(x + inset, y, x + width - inset, y + height, top, bottom);
         context.fillGradient(x, y + inset, x + width, y + height - inset, top, bottom);

         for (int row = 1; row < inset; row++) {
            int edge = inset - (int)Math.sqrt(Math.max(0, inset * inset - (inset - row) * (inset - row)));
            int color = mix(top, bottom, (float)row / Math.max(1, height));
            context.fill(x + edge, y + row, x + width - edge, y + row + 1, color);
            int bottomRow = y + height - row - 1;
            color = mix(top, bottom, (float)(height - row) / Math.max(1, height));
            context.fill(x + edge, bottomRow, x + width - edge, bottomRow + 1, color);
         }
      }
   }

   private static void roundedFill(GuiGraphics context, int x, int y, int width, int height, int radius, int color) {
      roundedGradient(context, x, y, width, height, radius, color, color);
   }

   private static void roundedOutline(GuiGraphics context, int x, int y, int width, int height, int radius, int color) {
      if (width >= 2 && height >= 2) {
         int inset = Math.min(Math.max(1, radius / 2), Math.max(1, width / 4));
         context.fill(x + inset, y, x + width - inset, y + 1, color);
         context.fill(x + inset, y + height - 1, x + width - inset, y + height, color);
         context.fill(x, y + inset, x + 1, y + height - inset, color);
         context.fill(x + width - 1, y + inset, x + width, y + height - inset, color);
      }
   }

   private static int withOpacity(long argb, double opacity) {
      int color = (int)argb;
      int alpha = color >>> 24 & 0xFF;
      return color & 16777215 | (int)Math.round(alpha * opacity) << 24;
   }

   private static int withAlpha(int color, int alpha) {
      return color & 16777215 | alpha << 24;
   }

   private static int mix(int a, int b, float amount) {
      float t = Math.max(0.0F, Math.min(1.0F, amount));
      int aa = (int)((a >>> 24 & 0xFF) + ((b >>> 24 & 0xFF) - (a >>> 24 & 0xFF)) * t);
      int ar = (int)((a >>> 16 & 0xFF) + ((b >>> 16 & 0xFF) - (a >>> 16 & 0xFF)) * t);
      int ag = (int)((a >>> 8 & 0xFF) + ((b >>> 8 & 0xFF) - (a >>> 8 & 0xFF)) * t);
      int ab = (int)((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return aa << 24 | ar << 16 | ag << 8 | ab;
   }
}
