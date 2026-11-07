package io.duckmc.ui.widget;

import io.duckmc.ui.render.NeonUi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class NeonField extends EditBox {
   private final int accent;

   public NeonField(Font font, int x, int y, int width, int height, Component name, int accent) {
      super(font, x, y, width, height, name);
      this.accent = accent;
      this.setBordered(false);
   }

   public void renderWidget(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      draw.fill(x + 1, y + 1, x + w - 1, y + h - 1, -536540654);
      NeonUi.outline(draw, x, y, w, h, NeonUi.alpha(this.accent, this.isFocused() ? 255 : 150));
      if (this.isFocused()) {
         NeonUi.glow(draw, x, y, w, h, this.accent, 0.7F);
      }

      draw.pose().pushMatrix();
      draw.pose().translate(4.0F, (h - 8) / 2);
      super.renderWidget(draw, mouseX - 4, mouseY - (h - 8) / 2, delta);
      draw.pose().popMatrix();
   }
}
