package io.duckmc.ui.widget;

import io.duckmc.ui.render.NeonUi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class PerformanceButton extends Button {
   private final int tab;
   private float hover;

   private PerformanceButton(PerformanceButton.StyleBuilder builder) {
      super(builder.x, builder.y, builder.width, builder.height, builder.message, builder.action, DEFAULT_NARRATION);
      this.tab = builder.tab;
   }

   public static PerformanceButton.StyleBuilder create(Component message, OnPress action) {
      return new PerformanceButton.StyleBuilder(message, action);
   }

   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      Minecraft client = Minecraft.getInstance();
      boolean selected = this.tab >= 0 && !this.active;
      this.hover = NeonUi.ease(this.hover, this.active && (this.isHovered() || this.isFocused() && client.getLastInputType().isKeyboard()), delta);
      String label = this.getMessage().getString();
      NeonUi.bevel(draw, x, y, w, h, -13703542, this.hover, selected || this.active && label.equals("Save"), this.active);
      String glyph = label.equals("Back") ? "← " : (label.equals("Save") ? "✔ " : "");
      draw.drawCenteredString(client.font, glyph + label, x + w / 2, y + (h - 8) / 2, !this.active && !selected ? -7629407 : -1);
   }

   @Environment(EnvType.CLIENT)
   public static final class StyleBuilder {
      private final Component message;
      private final OnPress action;
      private int x;
      private int y;
      private int width;
      private int height;
      private int tab = -1;
      private boolean gold;

      private StyleBuilder(Component message, OnPress action) {
         this.message = message;
         this.action = action;
      }

      public PerformanceButton.StyleBuilder dimensions(int x, int y, int width, int height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         return this;
      }

      public PerformanceButton.StyleBuilder tab(int index) {
         this.tab = index;
         return this;
      }

      public PerformanceButton.StyleBuilder gold() {
         this.gold = true;
         return this;
      }

      public PerformanceButton build() {
         return new PerformanceButton(this);
      }
   }
}
