package io.duckmc.ui.widget;

import io.duckmc.ui.render.NeonUi;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public final class SettingsCard extends Button {
   private final Component description;
   private final ItemStack icon;
   private final int accent;
   private float hover;

   public SettingsCard(Component title, Component description, ItemStack icon, int accent, int x, int y, int width, int height, Runnable action) {
      super(x, y, width, height, title, b -> action.run(), DEFAULT_NARRATION);
      this.description = description;
      this.icon = icon;
      this.accent = accent;
   }

   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      Font font = Minecraft.getInstance().font;
      this.hover = NeonUi.ease(this.hover, this.isHovered() || this.isFocused(), delta);
      NeonUi.bevel(draw, x, y, w, h, this.accent, this.hover, false, true);
      NeonUi.corners(draw, x, y, w, h, this.accent);
      Matrix3x2fStack matrices = draw.pose();
      matrices.pushMatrix();

      try {
         matrices.translate(x + 10, y + (h - 32) / 2.0F);
         matrices.scale(2.0F);
         draw.renderItem(this.icon, 0, 0);
      } finally {
         matrices.popMatrix();
      }

      int textX = x + 50;
      int textWidth = Math.max(16, w - 58);
      List<FormattedCharSequence> lines = font.split(this.description, textWidth);
      int shown = Math.min(lines.size(), Math.max(1, (h - 26) / 10));
      int top = y + (h - 12 - shown * 10) / 2;
      draw.drawString(font, font.plainSubstrByWidth(this.getMessage().getString(), textWidth), textX, top, -1);

      for (int i = 0; i < shown; i++) {
         draw.drawString(font, lines.get(i), textX, top + 12 + i * 10, -3879722, false);
      }
   }
}
