package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.pvp.PvpPolicy;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.render.UiText;
import io.duckmc.ui.widget.SectionButton;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class HudElementsScreen extends DuckUiScreen {
   private static final Map<String, String> NAMES = Map.of(
      "shield_status", "Shield Status", "target_info", "Target Info", "armor_status", "Armor Status", "totem_status", "Totem Status"
   );
   private final Screen parent;
   private final DuckMcRuntime runtime;

   public HudElementsScreen(Screen parent, DuckMcRuntime runtime) {
      super(Component.literal("HUD Elements"), runtime);
      this.parent = parent;
      this.runtime = runtime;
   }

   public List<String> elements() {
      return PvpPolicy.VISIBLE_CARDS.stream().filter(this.runtime.pvp()::enabled).toList();
   }

   protected void init() {
      this.prepareLayout();
      int y = 46;

      for (String id : this.elements()) {
         this.addRenderableWidget(
            SectionButton.create(
                  this.text(NAMES.get(id)), b -> this.minecraft.setScreen(new PvpHudEditorScreen(this, this.runtime, id)), SectionPalette.WHITE_GOLD
               )
               .dimensions(8, y, this.width - 16, 22)
               .build()
         );
         y += 26;
      }

      this.addRenderableWidget(
         SectionButton.create(this.text("Back"), b -> this.onClose(), SectionPalette.WHITE_GOLD).dimensions(8, this.height - 28, 80, 20).build()
      );
      this.addSectionSidebar();
   }

   private Component text(String value) {
      return UiText.styled(Component.literal(value), this.runtime.theme());
   }

   @Override
   protected void renderContents(GuiGraphics draw, int mx, int my, float delta) {
      int accent = (int)this.runtime.theme().accent();
      draw.drawString(this.font, this.text("HUD Elements"), 12, 12, accent);
      draw.drawString(this.font, this.font.plainSubstrByWidth("Position and style your active PvP HUD.", this.width - 24), 12, 26, -4208428, false);
      if (this.elements().isEmpty()) {
         draw.drawWordWrap(
            this.font,
            this.text("No HUD elements are active. Turn on Shield Status, Target Info, Armor Status or Totem Status in the PvP tab."),
            12,
            50,
            this.width - 24,
            -1775121,
            false
         );
      }

      super.renderContents(draw, mx, my, delta);
   }

   public void onClose() {
      this.runtime.settings().settle();
      this.minecraft.setScreen(this.parent);
   }
}
