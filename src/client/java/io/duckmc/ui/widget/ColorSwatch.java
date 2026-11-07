package io.duckmc.ui.widget;

import io.duckmc.ui.model.ColorPresets.Preset;
import io.duckmc.ui.render.NeonUi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ColorSwatch extends AbstractWidget {
   private final int color;
   private final boolean selected;
   private final Runnable action;

   public ColorSwatch(Preset preset, boolean selected, int x, int y, int width, int height, Runnable action) {
      super(x, y, width, height, Component.literal(preset.name()));
      this.color = (int)preset.argb();
      this.selected = selected;
      this.action = action;
      this.setTooltip(Tooltip.create(this.getMessage()));
   }

   protected void renderWidget(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      draw.fill(x + 1, y + 1, x + w - 1, y + h - 1, this.active ? this.color : NeonUi.alpha(this.color, 90));
      draw.fill(x + 2, y + 1, x + w - 2, y + 2, 1442840575);
      boolean hot = this.isHovered() || this.isFocused();
      NeonUi.outline(draw, x, y, w, h, this.selected ? -1 : (hot ? -2563348 : -11116436));
      if (this.selected) {
         NeonUi.glow(draw, x, y, w, h, -1, 1.0F);
         int cx = x + w / 2 - 3;
         int cy = y + h / 2 + 1;
         int mark = (this.color & 0xFF) + (this.color >> 8 & 0xFF) + (this.color >> 16 & 0xFF) > 380 ? -15723494 : -1;

         for (int i = 0; i < 3; i++) {
            draw.fill(cx + i, cy - 1 + i, cx + i + 1, cy + 1 + i, mark);
         }

         for (int i = 0; i < 4; i++) {
            draw.fill(cx + 3 + i, cy + 1 - i, cx + 4 + i, cy + 3 - i, mark);
         }
      }
   }

   public void onClick(MouseButtonEvent click, boolean doubled) {
      this.action.run();
   }

   protected void updateWidgetNarration(NarrationElementOutput builder) {
      this.defaultButtonNarrationText(builder);
   }
}
