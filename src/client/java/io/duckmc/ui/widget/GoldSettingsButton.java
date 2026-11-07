package io.duckmc.ui.widget;

import io.duckmc.ui.model.HomeLayout.Bounds;
import io.duckmc.ui.render.NeonUi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class GoldSettingsButton extends Button {
   private float hover;

   public GoldSettingsButton(Bounds bounds, Component message, OnPress action) {
      super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), message, action, DEFAULT_NARRATION);
   }

   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      Minecraft client = Minecraft.getInstance();
      this.hover = NeonUi.ease(this.hover, this.active && (this.isHovered() || this.isFocused() && client.getLastInputType().isKeyboard()), delta);
      NeonUi.bevel(draw, x, y, w, h, -15058, this.hover, false, this.active);
      draw.drawCenteredString(client.font, Component.literal("⚙ ").append(this.getMessage()), x + w / 2, y + (h - 8) / 2, this.active ? -1 : -3223858);
   }
}
