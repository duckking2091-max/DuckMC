package io.duckmc.hud;

import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.HudPlacement;
import io.duckmc.ui.render.GlassRenderer;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public final class HudRenderer implements HudElement {
   private static final int CARD_WIDTH = 126;
   private static final int CARD_HEIGHT = 24;
   private final DuckMcRuntime runtime;

   public HudRenderer(DuckMcRuntime runtime) {
      this.runtime = Objects.requireNonNull(runtime, "runtime");
   }

   public void render(GuiGraphics context, DeltaTracker tickCounter) {
      if (this.runtime.hudVisible()) {
         this.renderPreview(context);
      }
   }

   public void renderPreview(GuiGraphics context) {
      int viewportWidth = context.guiWidth();
      int viewportHeight = context.guiHeight();
      HudPlacement placement = this.runtime.hudPlacement().clamp(viewportWidth, viewportHeight, 126, 24);
      int x = (int)Math.round(placement.x() * viewportWidth);
      int y = (int)Math.round(placement.y() * viewportHeight);
      Matrix3x2fStack matrices = context.pose();
      matrices.pushMatrix();

      try {
         matrices.translate(x, y);
         matrices.scale((float)placement.scale());
         GlassRenderer.panel(context, 0, 0, 126, 24, this.runtime.theme(), true, this.runtime.config().activeSettings().get("hud.opacity").getAsDouble());
         context.fill(0, 0, 3, 24, (int)this.runtime.theme().accent());
         context.drawString(
            Minecraft.getInstance().font,
            Component.translatable("hud.duckmc.foundation"),
            10,
            8,
            this.runtime.config().activeSettings().get("hud.color").getAsInt()
         );
      } finally {
         matrices.popMatrix();
      }
   }
}
