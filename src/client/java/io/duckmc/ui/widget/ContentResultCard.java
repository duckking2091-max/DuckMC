package io.duckmc.ui.widget;

import com.mojang.blaze3d.platform.NativeImage;
import io.duckmc.content.ContentResult;
import io.duckmc.ui.render.NeonUi;
import io.duckmc.ui.screen.ContentThumbnails;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ContentResultCard extends AbstractWidget {
   private final ContentResult result;
   private final Runnable action;
   private final ContentThumbnails images;
   private float hover;

   public ContentResultCard(ContentResult result, Runnable action, ContentThumbnails images, int x, int y, int width, int height) {
      super(x, y, width, height, Component.literal(result.title() + " — " + result.provider()));
      this.result = result;
      this.action = action;
      this.images = images;
      this.setTooltip(Tooltip.create(this.getMessage()));
   }

   public ContentResult result() {
      return this.result;
   }

   protected void renderWidget(GuiGraphics draw, int mx, int my, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      boolean highlight = this.isFocused() || this.isHovered();
      this.hover = NeonUi.ease(this.hover, highlight, delta);
      draw.fill(x + 1, y + 1, x + w - 1, y + h - 1, -536210396);
      NeonUi.outline(draw, x, y, w, h, NeonUi.alpha(-13653761, 110 + (int)(145.0F * this.hover)));
      NeonUi.glow(draw, x, y, w, h, -13653761, this.hover * 0.8F);
      boolean compact = h < 72;
      int imageHeight = compact ? h - 8 : Math.max(12, h - 38);
      int imageWidth = compact ? imageHeight : w - 6;
      Optional<Identifier> image = this.images.image(this.result.provider(), this.result.id());
      if (image.isPresent()) {
         DynamicTexture texture = (DynamicTexture)Minecraft.getInstance().getTextureManager().getTexture(image.get());
         NativeImage bitmap = texture.getPixels();
         double fit = Math.min((double)imageWidth / bitmap.getWidth(), (double)imageHeight / bitmap.getHeight());
         int dw = Math.max(1, (int)(bitmap.getWidth() * fit));
         int dh = Math.max(1, (int)(bitmap.getHeight() * fit));
         draw.blit(RenderPipelines.GUI_TEXTURED, image.get(), x + 3 + (imageWidth - dw) / 2, y + 3 + (imageHeight - dh) / 2, 0.0F, 0.0F, dw, dh, dw, dh);
      } else {
         draw.drawCenteredString(
            Minecraft.getInstance().font,
            this.images.loading(this.result.provider(), this.result.id()) ? "Loading…" : (compact ? "No art" : "No preview"),
            x + 3 + imageWidth / 2,
            y + 3 + imageHeight / 2 - 4,
            -5588020
         );
      }

      Font renderer = Minecraft.getInstance().font;
      int textX = compact ? x + imageWidth + 10 : x + 5;
      draw.drawString(renderer, renderer.plainSubstrByWidth(this.result.title(), x + w - textX - 5), textX, compact ? y + (h - 30) / 2 : y + h - 33, -1);
      draw.drawString(
         renderer,
         renderer.plainSubstrByWidth("by " + this.result.author(), x + w - textX - 5),
         textX,
         compact ? y + (h - 30) / 2 + 10 : y + h - 23,
         -5588020,
         false
      );
      draw.drawString(renderer, "View details", textX, compact ? y + (h - 30) / 2 + 20 : y + h - 12, -7540481);
   }

   public void onClick(MouseButtonEvent click, boolean doubled) {
      this.action.run();
   }

   public boolean keyPressed(KeyEvent input) {
      if (!this.active || !this.visible || input.key() != 257 && input.key() != 335 && input.key() != 32) {
         return super.keyPressed(input);
      }

      this.action.run();
      return true;
   }

   protected void updateWidgetNarration(NarrationElementOutput builder) {
      this.defaultButtonNarrationText(builder);
   }
}
