package io.duckmc.ui.render;

import io.duckmc.ui.model.RoundedButtonShape;
import io.duckmc.ui.model.UiColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class MenuArtwork {
   private MenuArtwork() {
   }

   public static void roundedButton(GuiGraphics draw, int x, int y, int width, int height, int accent, boolean highlighted) {
      int top = UiColors.darker(accent);
      int bottom = UiColors.darker(top);
      int rim = highlighted ? -1 : bottom;

      for (int row = 0; row < height; row++) {
         int inset = RoundedButtonShape.inset(row, height);
         draw.fill(x + inset, y + row, x + width - inset, y + row + 1, rim);
         if (row > 0 && row < height - 1) {
            double t = (double)row / (height - 1);
            int color = -16777216;

            for (int shift = 0; shift <= 16; shift += 8) {
               int a = top >>> shift & 0xFF;
               int b = bottom >>> shift & 0xFF;
               color |= (int)Math.round(a + (b - a) * t) << shift;
            }

            draw.fill(x + inset + 1, y + row, x + width - inset - 1, y + row + 1, color);
         }
      }
   }

   public static Identifier texture(String name) {
      return Identifier.fromNamespaceAndPath("duckmc", "textures/gui/home/" + name + ".png");
   }

   public static void image(GuiGraphics draw, Identifier texture, int x, int y, int width, int height, int sourceWidth, int sourceHeight, boolean cover) {
      double scale = cover
         ? Math.max((double)width / sourceWidth, (double)height / sourceHeight)
         : Math.min((double)width / sourceWidth, (double)height / sourceHeight);
      int w = Math.max(1, (int)Math.ceil(sourceWidth * scale));
      int h = Math.max(1, (int)Math.ceil(sourceHeight * scale));
      draw.blit(RenderPipelines.GUI_TEXTURED, texture, x + (width - w) / 2, y + (height - h) / 2, 0.0F, 0.0F, w, h, w, h);
   }

   public static void border(GuiGraphics draw, int x, int y, int width, int height, int color) {
      draw.fill(x + 2, y, x + width - 2, y + 1, color);
      draw.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
      draw.fill(x, y + 2, x + 1, y + height - 2, color);
      draw.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
      draw.fill(x + 1, y + 1, x + 2, y + 2, color);
      draw.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
      draw.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
      draw.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
   }

   public static void goldBorder(GuiGraphics draw, int x, int y, int width, int height) {
      int top = UiColors.whiteGold(0.0);
      int bottom = UiColors.whiteGold(1.0);
      draw.fill(x + 2, y, x + width - 2, y + 2, top);
      draw.fill(x + 2, y + height - 2, x + width - 2, y + height, bottom);
      draw.fillGradient(x, y + 2, x + 2, y + height - 2, top, bottom);
      draw.fillGradient(x + width - 2, y + 2, x + width, y + height - 2, top, bottom);
      draw.fill(x + 1, y + 1, x + 2, y + 2, top);
      draw.fill(x + width - 2, y + 1, x + width - 1, y + 2, top);
      draw.fill(x + 1, y + height - 2, x + 2, y + height - 1, bottom);
      draw.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, bottom);
   }

   public static void icon(GuiGraphics draw, String kind, int x, int y, int size, int color) {
      int[] rows = switch (kind) {
         case "performance", "settings" -> new int[]{
            1632, 1632, 16380, 32766, 16380, 31806, 64575, 63519, 63519, 64575, 31806, 16380, 32766, 16380, 1632, 1632
         };
         case "pvp" -> new int[]{49155, 57351, 61455, 30750, 15420, 7800, 4080, 2016, 2016, 4080, 15420, 32382, 27702, 6168, 12300, 24582};
         case "content" -> new int[]{384, 2016, 7800, 30750, 24582, 30750, 32182, 26502, 24966, 24966, 24966, 24966, 12684, 7608, 2016, 384};
         default -> new int[]{224, 480, 960, 1920, 3840, 8190, 16380, 32760, 496, 992, 1984, 1920, 3840, 3584, 3072, 2048};
      };
      draw.pose().pushMatrix();
      draw.pose().translate(x, y);
      draw.pose().scale(size / 16.0F);

      for (int row = 0; row < 16; row++) {
         int start = -1;

         for (int col = 0; col <= 16; col++) {
            boolean filled = col < 16 && (rows[row] & 1 << 15 - col) != 0;
            if (filled && start < 0) {
               start = col;
            }

            if (!filled && start >= 0) {
               draw.fill(start, row, col, row + 1, color);
               start = -1;
            }
         }
      }

      draw.pose().popMatrix();
   }
}
