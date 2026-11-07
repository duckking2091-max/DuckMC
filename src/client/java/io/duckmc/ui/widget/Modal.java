package io.duckmc.ui.widget;

import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.model.Theme;
import io.duckmc.ui.render.GlassRenderer;
import io.duckmc.ui.render.MenuArtwork;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class Modal {
   private final Component title;
   private final Component body;
   private final Theme theme;
   private boolean open = true;
   private int x;
   private int y;
   private int width;
   private int height;
   private final Button done;

   public Modal(Component title, Component body, Theme theme) {
      this.title = Objects.requireNonNull(title, "title");
      this.body = Objects.requireNonNull(body, "body");
      this.theme = Objects.requireNonNull(theme, "theme");
      this.done = SectionButton.create(Component.translatable("gui.done"), b -> this.close(), SectionPalette.WHITE_GOLD).dimensions(0, 0, 92, 20).build();
   }

   public void render(GuiGraphics context, int viewportWidth, int viewportHeight, int mouseX, int mouseY) {
      context.fill(0, 0, viewportWidth, viewportHeight, -1710946549);
      this.width = Math.min(420, Math.max(240, viewportWidth - 40));
      this.height = Math.min(178, Math.max(132, viewportHeight - 40));
      this.x = (viewportWidth - this.width) / 2;
      this.y = (viewportHeight - this.height) / 2;
      GlassRenderer.panel(context, this.x, this.y, this.width, this.height, this.theme, true);
      MenuArtwork.goldBorder(context, this.x, this.y, this.width, this.height);
      Font textRenderer = Minecraft.getInstance().font;
      context.drawString(textRenderer, this.title, this.x + 18, this.y + 18, -1);
      context.drawWordWrap(textRenderer, this.body, this.x + 18, this.y + 43, this.width - 36, -4011307, false);
      int buttonWidth = 92;
      int buttonX = this.x + this.width - buttonWidth - 16;
      int buttonY = this.y + this.height - 31;
      this.done.setRectangle(buttonWidth, 20, buttonX, buttonY);
      this.done.render(context, mouseX, mouseY, 0.0F);
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() != 256 && input.key() != 257 && input.key() != 335 && input.key() != 32) {
         return true;
      }

      this.close();
      return true;
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      int buttonWidth = 92;
      int buttonX = this.x + this.width - buttonWidth - 16;
      int buttonY = this.y + this.height - 31;
      if (click.x() >= buttonX && click.x() < buttonX + buttonWidth && click.y() >= buttonY && click.y() < buttonY + 20) {
         this.close();
      }

      return true;
   }

   public void close() {
      this.open = false;
   }

   public boolean isOpen() {
      return this.open;
   }

   public Component narration() {
      return this.title.copy().append(". ").append(this.body);
   }
}
