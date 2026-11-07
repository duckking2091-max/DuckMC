package io.duckmc.ui.widget;

import io.duckmc.ui.model.RoundedButtonShape;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.model.UiColors;
import io.duckmc.ui.render.NeonUi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
public final class SectionButton extends Button {
   private final SectionPalette palette;
   private boolean selected;
   private float hover;

   public void markSelected(boolean value) {
      this.selected = value;
   }

   private SectionButton(SectionButton.StyleBuilder b) {
      super(b.x, b.y, b.width, b.height, b.message, b.action, DEFAULT_NARRATION);
      this.palette = b.palette;
      this.selected = b.selected;
   }

   public static SectionButton.StyleBuilder create(Component message, OnPress action, SectionPalette palette) {
      return new SectionButton.StyleBuilder(message, action, palette);
   }

   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      if (this.palette != SectionPalette.WHITE_GOLD) {
         Minecraft client = Minecraft.getInstance();

         int accent = switch (this.palette) {
            case GREEN -> -13703542;
            case RED -> -50340;
            case GOLD -> -15058;
            default -> -13653761;
         };
         this.hover = NeonUi.ease(this.hover, this.active && (this.isHovered() || this.isFocused() && client.getLastInputType().isKeyboard()), delta);
         NeonUi.bevel(draw, x, y, w, h, accent, this.hover, this.selected || this.active && this.getMessage().getString().equals("Save"), this.active);

         String glyph = switch (this.getMessage().getString()) {
            case "Config", "Less" -> "⚙ ";
            case "Back" -> "← ";
            case "Save" -> "✔ ";
            case "Search" -> "⌕ ";
            case "Next" -> "→ ";
            default -> "";
         };
         MutableComponent text = Component.literal(glyph).append(this.getMessage());
         Font font = client.font;
         if (font.width(text) > w - 4) {
            text = this.getMessage().copy();
         }

         draw.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, !this.active && !this.selected ? -7629407 : -1);
      } else {
         int rim = !this.selected && !this.isHovered() && (!this.isFocused() || !Minecraft.getInstance().getLastInputType().isKeyboard())
            ? UiColors.darker(this.palette.colorAt(0, 0, w, h))
            : -1;

         for (int row = 0; row < h; row++) {
            int inset = RoundedButtonShape.inset(row, h);
            draw.fill(x + inset, y + row, x + w - inset, y + row + 1, rim);
         }

         if (this.palette.horizontal) {
            for (int column = 1; column < w - 1; column++) {
               int inset = 1 + RoundedButtonShape.inset(column, w);
               int color = this.palette.colorAt(column, 0, w, h);
               draw.fill(x + column, y + inset, x + column + 1, y + h - inset, this.active ? color : UiColors.darker(color));
            }
         } else {
            for (int row = 1; row < h - 1; row++) {
               int inset = 1 + RoundedButtonShape.inset(row, h);
               int color = this.palette.colorAt(0, row, w, h);
               draw.fill(x + inset, y + row, x + w - inset, y + row + 1, this.active ? color : UiColors.darker(color));
            }
         }

         Font font = Minecraft.getInstance().font;
         int textX = x + w / 2;
         int textY = y + (h - 8) / 2;
         draw.drawCenteredString(font, this.getMessage(), textX, textY, this.active ? -1 : -4472120);
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class StyleBuilder {
      private final Component message;
      private final OnPress action;
      private SectionPalette palette;
      private int x;
      private int y;
      private int width;
      private int height;
      private boolean selected;

      public SectionButton.StyleBuilder selected(boolean value) {
         this.selected = value;
         return this;
      }

      private StyleBuilder(Component message, OnPress action, SectionPalette palette) {
         this.message = message;
         this.action = action;
         this.palette = palette;
      }

      public SectionButton.StyleBuilder dimensions(int x, int y, int width, int height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         return this;
      }

      public SectionButton.StyleBuilder gold() {
         this.palette = SectionPalette.WHITE_GOLD;
         return this;
      }

      public SectionButton build() {
         return new SectionButton(this);
      }
   }
}
