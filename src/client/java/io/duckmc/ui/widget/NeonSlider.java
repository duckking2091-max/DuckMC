package io.duckmc.ui.widget;

import io.duckmc.ui.render.NeonUi;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class NeonSlider extends AbstractWidget {
   private static final int VALUE_WIDTH = 50;
   private final double min;
   private final double max;
   private final int accent;
   private final DoubleFunction<String> format;
   private final DoubleConsumer changed;
   private double value;

   public NeonSlider(
      Component label, double value, double min, double max, int accent, int x, int y, int width, DoubleFunction<String> format, DoubleConsumer changed
   ) {
      super(x, y, width, 14, label);
      this.value = value;
      this.min = min;
      this.max = max;
      this.accent = accent;
      this.format = format;
      this.changed = changed;
   }

   public double value() {
      return this.value;
   }

   private int trackWidth() {
      return this.getWidth() - 50 - 4;
   }

   protected void renderWidget(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY() + 5;
      int w = this.trackWidth();
      int knob = x + (int)((this.value - this.min) / (this.max - this.min) * (w - 6));
      draw.fill(x, y, x + w, y + 4, -14208965);
      draw.fill(x, y, knob + 3, y + 4, this.active ? this.accent : -11116436);
      draw.fill(knob, y - 3, knob + 6, y + 7, -1);
      NeonUi.outline(draw, knob, y - 3, 6, 10, !this.isHovered() && !this.isFocused() ? -7498077 : this.accent);
      Font font = Minecraft.getInstance().font;
      String text = this.format.apply(this.value);
      draw.drawString(font, text, this.getX() + this.getWidth() - font.width(text), this.getY() + 3, -1);
   }

   private void set(double next) {
      this.value = Math.max(this.min, Math.min(this.max, next));
      this.changed.accept(this.value);
   }

   private void point(double mouseX) {
      this.set(this.min + (mouseX - this.getX() - 3.0) / (this.trackWidth() - 6) * (this.max - this.min));
   }

   public void onClick(MouseButtonEvent click, boolean doubled) {
      this.point(click.x());
   }

   protected void onDrag(MouseButtonEvent click, double deltaX, double deltaY) {
      this.point(click.x());
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() != 263 && input.key() != 262) {
         return super.keyPressed(input);
      }

      this.set(this.value + (input.key() == 263 ? -0.05 : 0.05) * (this.max - this.min));
      return true;
   }

   protected void updateWidgetNarration(NarrationElementOutput builder) {
      this.defaultButtonNarrationText(builder);
   }
}
