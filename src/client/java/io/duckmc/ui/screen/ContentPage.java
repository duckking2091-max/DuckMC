package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.ScreenRoute;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.render.UiText;
import io.duckmc.ui.widget.SectionButton;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
abstract class ContentPage extends DuckUiScreen {
   protected final DuckMcRuntime runtime;
   protected final Screen parent;
   protected int x;
   protected int w;

   ContentPage(DuckMcRuntime runtime, String title, Screen parent) {
      super(Component.literal(title), runtime);
      this.runtime = runtime;
      this.parent = parent;
   }

   protected void layoutPage() {
      this.prepareLayout();
      this.x = Math.max(14, (this.width - 600) / 2);
      this.w = Math.min(600, this.width - 28);
      this.addSectionSidebar();
   }

   protected Component text(String value) {
      return UiText.styled(Component.literal(value), this.runtime.theme());
   }

   protected Button button(String label, int bx, int y, int width, Runnable action) {
      return (Button)this.addRenderableWidget(
         SectionButton.create(this.text(label), ignored -> action.run(), SectionPalette.BLUE).dimensions(bx, y, width, 20).build()
      );
   }

   protected void rebuild() {
      this.clearWidgets();
      this.init();
   }

   protected void line(GuiGraphics draw, String value, int y) {
      List<FormattedCharSequence> lines = this.font.split(this.text(value), this.w - 12);
      if (!lines.isEmpty()) {
         draw.drawString(this.font, lines.getFirst(), this.x + 6, y, -2826520);
      }
   }

   @Override
   protected void renderContents(GuiGraphics draw, int mx, int my, float delta) {
      draw.drawString(this.font, UiText.styled(this.title, this.runtime.theme()), this.x + 4, 20, -13653761);
      this.renderBody(draw);
      super.renderContents(draw, mx, my, delta);
   }

   protected void renderBody(GuiGraphics draw) {
   }

   public void onClose() {
      if (this.parent == null) {
         this.runtime.router().open(ScreenRoute.MAIN);
      } else {
         this.minecraft.setScreen(this.parent);
      }
   }
}
