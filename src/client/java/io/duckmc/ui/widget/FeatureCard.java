package io.duckmc.ui.widget;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.modules.ModuleId;
import io.duckmc.modules.ModuleRegistry;
import io.duckmc.modules.ModuleState;
import io.duckmc.ui.model.Theme;
import io.duckmc.ui.render.GlassRenderer;
import io.duckmc.ui.render.MenuArtwork;
import io.duckmc.ui.render.NeonUi;
import io.duckmc.ui.render.UiText;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public final class FeatureCard extends AbstractWidget {
   private final Component description;
   private final Component badge;
   private final int accent;
   private final Theme theme;
   private final Runnable action;
   private final ModuleRegistry registry;
   private final ModuleId moduleId;
   private final boolean expandable;
   private boolean expanded;
   private String artwork;
   private float hover;

   public FeatureCard illustrated(String artwork) {
      this.artwork = Objects.requireNonNull(artwork);
      return this;
   }

   public static FeatureCard navigation(
      int x, int y, int width, int height, Component title, Component description, Component badge, int accent, Theme theme, Runnable action
   ) {
      return new FeatureCard(x, y, width, height, title, description, badge, accent, theme, Objects.requireNonNull(action, "action"), null, null, false);
   }

   public static FeatureCard module(
      int x,
      int y,
      int width,
      int height,
      Component title,
      Component description,
      int accent,
      Theme theme,
      ModuleRegistry registry,
      ModuleId moduleId,
      boolean expandable
   ) {
      Objects.requireNonNull(registry, "registry");
      Objects.requireNonNull(moduleId, "moduleId");
      return new FeatureCard(
         x, y, width, height, title, description, Component.empty(), accent, theme, () -> toggle(registry, moduleId), registry, moduleId, expandable
      );
   }

   private FeatureCard(
      int x,
      int y,
      int width,
      int height,
      Component title,
      Component description,
      Component badge,
      int accent,
      Theme theme,
      Runnable action,
      ModuleRegistry registry,
      ModuleId moduleId,
      boolean expandable
   ) {
      super(x, y, width, height, Objects.requireNonNull(title, "title"));
      this.description = Objects.requireNonNull(description, "description");
      this.badge = Objects.requireNonNull(badge, "badge");
      this.accent = accent;
      this.theme = Objects.requireNonNull(theme, "theme");
      this.action = Objects.requireNonNull(action, "action");
      this.registry = registry;
      this.moduleId = moduleId;
      this.expandable = expandable;
   }

   protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
      if (this.artwork != null) {
         this.renderIllustrated(context);
      } else {
         GlassRenderer.card(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.accent, this.theme, this.isFocused(), this.isHovered());
         Font textRenderer = Minecraft.getInstance().font;
         int textX = this.getX() + 14;
         context.drawString(textRenderer, UiText.styled(this.getMessage(), this.theme), textX, this.getY() + 12, -526086);
         Component effectiveBadge = this.registry == null ? this.badge : this.moduleBadge();
         int badgeWidth = Math.min(84, textRenderer.width(effectiveBadge) + 12);
         int badgeX = this.getRight() - badgeWidth - 10;
         int badgeY = this.getBottom() - 22;
         GlassRenderer.pill(context, badgeX, badgeY, badgeWidth, 17, 1006632960, this.accent);
         context.drawString(textRenderer, effectiveBadge, badgeX + 6, badgeY + 5, -1446414);
         int descriptionWidth = Math.max(20, this.getWidth() - 28);
         DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
         Component styledDescription = UiText.styled(this.description, this.theme);
         List<FormattedCharSequence> lines = runtime == null
            ? textRenderer.split(styledDescription, descriptionWidth)
            : runtime.performance().wrap(styledDescription, descriptionWidth);
         int lineY = this.getY() + 27;
         int limit = this.getHeight() < 76 ? 1 : (this.expanded ? 3 : 2);

         for (int i = 0; i < Math.min(limit, lines.size()); i++) {
            context.drawString(textRenderer, lines.get(i), textX, lineY + i * 10, -4734772, false);
         }

         if (this.expandable) {
            Component hint = Component.translatable(this.expanded ? "ui.duckmc.collapse" : "ui.duckmc.expand");
            context.drawString(textRenderer, hint, textX, this.getBottom() - 13, -6773581, false);
         }
      }
   }

   private void renderIllustrated(GuiGraphics draw) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      int padding = w < 100 ? 7 : 12;

      int sourceWidth = switch (this.artwork) {
         case "performance" -> 3440;
         case "pvp", "content" -> 800;
         default -> 1536;
      };

      int sourceHeight = switch (this.artwork) {
         case "performance" -> 1369;
         case "pvp" -> 500;
         case "content" -> 450;
         default -> 1024;
      };
      String image = this.artwork.equals("content") ? "worlds" : this.artwork;
      draw.enableScissor(x, y, x + w, y + h);
      MenuArtwork.image(draw, MenuArtwork.texture(image), x, y, w, h, sourceWidth, sourceHeight, true);
      draw.fillGradient(x, y, x + w, y + h, 536872460, -184548343);
      draw.fillGradient(x, y + h / 3, x + w, y + h, 0, -1073741824);
      draw.disableScissor();
      boolean keyboardFocus = this.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
      boolean highlight = this.isHovered() || keyboardFocus;
      this.hover = NeonUi.ease(this.hover, highlight, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
      NeonUi.outline(draw, x, y, w, h, NeonUi.alpha(this.accent, 170 + (int)(85.0F * this.hover)));
      NeonUi.glow(draw, x, y, w, h, this.accent, 0.3F + 0.7F * this.hover);
      NeonUi.corners(draw, x, y, w, h, this.accent);
      if (highlight) {
         NeonUi.chase(draw, x, y, w, h, -1, System.currentTimeMillis());
      }

      Font renderer = Minecraft.getInstance().font;
      int buttonHeight = h < 110 ? 16 : 23;
      int buttonY = y + h - padding - buttonHeight;
      int iconSize = Math.max(12, Math.min(30, w / 5));
      int iconY = y + Math.max(8, Math.min(h / 4, h - buttonHeight - padding - 62));
      MenuArtwork.icon(draw, this.artwork, x + padding, iconY, iconSize, this.accent);
      float scale = w < 100 ? 0.8F : 1.0F;
      int titleY = iconY + iconSize + 6;
      int titleWidth = (int)((w - padding * 2) / scale);
      List<FormattedCharSequence> titleLines = renderer.split(UiText.styled(this.getMessage(), this.theme), titleWidth);
      draw.pose().pushMatrix();
      draw.pose().translate(x + padding, titleY);
      draw.pose().scale(scale);
      int maxLines = Math.max(1, Math.min(3, (int)((buttonY - titleY - 3) / (9.0F * scale))));

      for (int i = 0; i < Math.min(maxLines, titleLines.size()); i++) {
         draw.drawString(renderer, titleLines.get(i), 0, i * 10, -1);
      }

      draw.pose().popMatrix();
      int descriptionY = titleY + (int)(Math.min(maxLines, titleLines.size()) * 10 * scale) + 7;
      List<FormattedCharSequence> lines = renderer.split(UiText.styled(this.description, this.theme), w - padding * 2);

      for (int i = 0; i < Math.min(2, lines.size()) && descriptionY + i * 10 + 9 < buttonY - 5; i++) {
         draw.drawString(renderer, lines.get(i), x + padding, descriptionY + i * 10, -3089691);
      }

      NeonUi.bevel(draw, x + padding, buttonY, w - padding * 2, buttonHeight, this.accent, this.hover, false, true);
      draw.drawCenteredString(
         renderer, UiText.styled(Component.literal(w < 90 ? "Open" : "Open →"), this.theme), x + w / 2, buttonY + (buttonHeight - 8) / 2, -1
      );
   }

   public void onClick(MouseButtonEvent click, boolean doubled) {
      this.activate();
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.active && this.visible) {
         if (input.key() == 257 || input.key() == 335 || input.key() == 32) {
            this.activate();
            return true;
         } else if (this.expandable && input.key() == 262) {
            this.expanded = true;
            return true;
         } else if (this.expandable && input.key() == 263) {
            this.expanded = false;
            return true;
         } else {
            return super.keyPressed(input);
         }
      } else {
         return false;
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput builder) {
      this.defaultButtonNarrationText(builder);
      builder.add(NarratedElementType.HINT, this.description);
   }

   public boolean expanded() {
      return this.expanded;
   }

   private void activate() {
      this.action.run();
      this.playDownSound(Minecraft.getInstance().getSoundManager());
   }

   private Component moduleBadge() {
      return switch (this.registry.state(this.moduleId)) {
         case ENABLED -> Component.translatable("ui.duckmc.state.enabled");
         case ENABLING -> Component.translatable("ui.duckmc.state.enabling");
         case DISABLING -> Component.translatable("ui.duckmc.state.disabling");
         case UNAVAILABLE -> Component.translatable("ui.duckmc.state.unavailable");
         case FAILED_SAFE -> Component.translatable("ui.duckmc.state.failed_safe");
         case DISABLED -> Component.translatable("ui.duckmc.state.disabled");
         default -> throw new MatchException(null, null);
      };
   }

   private static void toggle(ModuleRegistry registry, ModuleId id) {
      ModuleState state = registry.state(id);
      if (state != ModuleState.UNAVAILABLE && state != ModuleState.FAILED_SAFE) {
         registry.setEnabled(id, state != ModuleState.ENABLED);
      }
   }
}
