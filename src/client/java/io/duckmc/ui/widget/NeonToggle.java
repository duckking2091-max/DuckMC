package io.duckmc.ui.widget;

import io.duckmc.ui.render.NeonUi;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class NeonToggle extends Button {
   private static final Map<String, Float> HANDLES = new HashMap<>();
   private final String key;
   private final Component name;
   private final String status;
   private final boolean on;
   private final int accent;
   private final int rowWidth;
   private float hover;

   public NeonToggle(
      String key, Component name, Component message, String status, boolean on, int accent, int x, int y, int width, int rowWidth, OnPress action
   ) {
      super(x, y, width, 20, message, action, DEFAULT_NARRATION);
      this.key = key;
      this.name = name;
      this.status = status;
      this.on = on;
      this.accent = accent;
      this.rowWidth = rowWidth;
   }

   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      Minecraft client = Minecraft.getInstance();
      Font font = client.font;
      boolean highlight = this.active && (this.isHovered() || this.isFocused() && client.getLastInputType().isKeyboard());
      this.hover = NeonUi.ease(this.hover, highlight, delta);
      NeonUi.row(draw, x, y, this.rowWidth, h, this.active ? this.accent : -11906466, this.hover);
      int switchX = x + w - 32;
      int switchY = y + 4;
      int reserved = this.status.isEmpty() ? 44 : 44 + font.width(this.status) + 6;
      int nameWidth = Math.max(8, w - reserved - 22);
      draw.drawString(font, font.plainSubstrByWidth(this.name.getString(), nameWidth), x + 8, y + 6, this.active ? -854278 : -7629407);
      int infoX = x + 8 + Math.min(font.width(this.name), nameWidth) + 5;
      draw.fill(infoX, y + 6, infoX + 7, y + 13, NeonUi.alpha(this.accent, this.active ? 95 : 45));
      draw.fill(infoX + 3, y + 7, infoX + 4, y + 8, -1);
      draw.fill(infoX + 3, y + 9, infoX + 4, y + 12, -1);
      if (!this.status.isEmpty()) {
         draw.drawString(font, this.status, switchX - font.width(this.status) - 6, y + 6, -4998202);
      }

      float target = this.on ? 1.0F : 0.0F;
      float handle = HANDLES.getOrDefault(this.key, target);
      handle += (target - handle) * Math.min(1.0F, delta * 0.5F + 0.08F);
      if (Math.abs(handle - target) < 0.01F) {
         handle = target;
      }

      HANDLES.put(this.key, handle);
      int track = NeonUi.mix(-13946565, this.active ? this.accent : -12762033, handle);
      draw.fill(switchX + 2, switchY, switchX + 26, switchY + 12, track);
      draw.fill(switchX, switchY + 2, switchX + 28, switchY + 10, track);
      draw.fill(switchX + 1, switchY + 1, switchX + 27, switchY + 11, track);
      if (this.active) {
         NeonUi.glow(draw, switchX, switchY, 28, 12, this.accent, handle * 0.8F);
      }

      int knob = switchX + 2 + Math.round(16.0F * handle);
      int knobColor = NeonUi.mix(-7498077, -1, handle);
      draw.fill(knob + 2, switchY + 2, knob + 8, switchY + 10, knobColor);
      draw.fill(knob + 1, switchY + 3, knob + 9, switchY + 9, knobColor);
   }
}
