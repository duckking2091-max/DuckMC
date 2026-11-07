package io.duckmc.ui.widget;

import io.duckmc.ui.render.MenuArtwork;
import io.duckmc.ui.render.NeonUi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class SidebarButton extends Button {
   private final String icon;
   private final int color;
   private final boolean selected;
   private float hover;

   public SidebarButton(int x, int y, Component name, String icon, int color, boolean selected, OnPress action) {
      super(x, y, 100, 22, name, action, DEFAULT_NARRATION);
      this.icon = icon;
      this.color = color;
      this.selected = selected;
      this.active = !selected;
   }

   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      boolean highlighted = this.isHovered() || this.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
      int x = this.getX();
      int y = this.getY();
      this.hover = NeonUi.ease(this.hover, highlighted || this.selected, delta);
      if (this.selected || this.hover > 0.02F) {
         draw.fill(x + 1, y + 1, x + this.getWidth() - 1, y + 21, NeonUi.alpha(this.color, this.selected ? 58 : (int)(34.0F * this.hover)));
         NeonUi.outline(draw, x, y, this.getWidth(), 22, NeonUi.alpha(this.color, this.selected ? 225 : (int)(130.0F * this.hover)));
      }

      if (this.selected) {
         NeonUi.glow(draw, x, y, this.getWidth(), 22, this.color, 0.75F);
         draw.fill(x - 3, y + 4, x - 1, y + 18, this.color);
      }

      MenuArtwork.icon(draw, this.icon, x + 5, y + 5, 12, this.color);
      draw.pose().pushMatrix();
      draw.pose().translate(x + 22, y + 7);
      draw.pose().scale(0.85F);
      draw.drawString(Minecraft.getInstance().font, this.getMessage(), 0, 0, !this.selected && !highlighted ? -4405294 : -1);
      draw.pose().popMatrix();
   }
}
