package io.duckmc.ui.widget;

import io.duckmc.ui.render.NeonUi;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
public final class ProfileCard extends AbstractWidget {
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT).withZone(ZoneId.systemDefault());
   private final String name;
   private final String description;
   private final long modified;
   private final boolean active;
   private final boolean selected;
   private final Runnable action;
   private float hover;

   public ProfileCard(String name, String description, long modified, boolean active, boolean selected, int width, int height, Runnable action) {
      super(0, 0, width, height, Component.literal(name + (active ? " (active)" : "")));
      this.name = name;
      this.description = description;
      this.modified = modified;
      this.active = active;
      this.selected = selected;
      this.action = action;
   }

   protected void renderWidget(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      Font font = Minecraft.getInstance().font;
      this.hover = NeonUi.ease(this.hover, this.isHovered() || this.isFocused(), delta);
      NeonUi.row(draw, x, y, w, h, -15058, this.selected ? 1.0F : this.hover);
      if (this.active) {
         NeonUi.glow(draw, x, y, w, h, -15058, 1.0F);
         NeonUi.corners(draw, x, y, w, h, -15058);
      } else if (this.selected) {
         NeonUi.outline(draw, x, y, w, h, -1);
      }

      int tile = 28;
      int tx = x + 7;
      int ty = y + 7;
      draw.fillGradient(tx, ty, tx + tile, ty + tile, -9547242, -14016240);
      NeonUi.outline(draw, tx, ty, tile, tile, -15058);
      draw.drawCenteredString(font, this.name.substring(0, 1).toUpperCase(Locale.ROOT), tx + tile / 2, ty + 10, -1);
      int textX = tx + tile + 7;
      int textWidth = w - tile - 40;
      draw.drawString(font, font.plainSubstrByWidth(this.name, textWidth), textX, y + 7, -1);
      draw.drawString(font, font.plainSubstrByWidth(this.description, w - tile - 20), textX, y + 20, -4208428, false);
      draw.drawString(font, this.modified > 0L ? DATE.format(Instant.ofEpochMilli(this.modified)) : "Not modified", x + 7, y + h - 12, -7365720, false);
      if (this.active) {
         int badge = font.width("Active") + 8;
         int bx = x + w - badge - 5;
         int by = y + h - 15;
         draw.fill(bx, by, bx + badge, by + 12, -15058);
         draw.drawString(font, "Active", bx + 4, by + 2, -15068155, false);
      }
   }

   public void onClick(MouseButtonEvent click, boolean doubled) {
      this.action.run();
   }

   public boolean keyPressed(KeyEvent input) {
      if (!this.active() || input.key() != 257 && input.key() != 32) {
         return super.keyPressed(input);
      }

      this.action.run();
      return true;
   }

   private boolean active() {
      return this.visible && super.active;
   }

   protected void updateWidgetNarration(NarrationElementOutput builder) {
      this.defaultButtonNarrationText(builder);
   }
}
