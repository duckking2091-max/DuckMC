package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class MaintenanceScreen extends Screen {
   private final Screen parent;
   private final DuckMcRuntime runtime;
   private String notice = "Resets affect the active profile unless stated otherwise.";

   public MaintenanceScreen(Screen parent, DuckMcRuntime runtime) {
      super(Component.literal("Reset / updates"));
      this.parent = parent;
      this.runtime = runtime;
   }

   protected void init() {
      int x = this.width / 2 - 130;
      String[] sections = new String[]{"ui", "category", "hud", "key", "performance", "module"};
      String[] labels = new String[]{"Reset appearance", "Reset colors", "Reset HUD", "Reset keys", "Reset performance values", "Reset modules"};

      for (int i = 0; i < sections.length; i++) {
         String section = sections[i];
         this.button(
            labels[i],
            x + i % 2 * 135,
            48 + i / 2 * 25,
            125,
            () -> this.stage(() -> this.runtime.settings().resetSection(section), "Reset " + section + " in the active profile?")
         );
      }

      this.button("Reset active profile", x, 123, 125, () -> this.stage(() -> this.runtime.settings().resetAll(), "Reset every setting in the active profile?"));
      this.button(
         "Reset acknowledgement",
         x + 135,
         123,
         125,
         () -> this.stage(() -> this.runtime.settings().resetAcknowledgement(), "Clear acknowledgement and disable Advantages in ALL profiles?")
      );
      if (this.runtime.settings().hasPendingReset()) {
         this.button("Confirm reset", x, 148, 125, () -> {
            this.runtime.settings().confirmReset();
            this.notice = "Reset applied.";
            this.rebuild();
         });
         this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            this.runtime.settings().cancelReset();
            this.notice = "Reset cancelled.";
            this.rebuild();
         }).bounds(x + 135, 148, 125, 20).build());
      }

      this.addRenderableWidget(
         Button.builder(Component.literal("Check updates"), b -> this.notice = "No release source configured. No network request was made.")
            .bounds(x, this.height - 30, 125, 20)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose()).bounds(x + 135, this.height - 30, 125, 20).build());
   }

   private void button(String label, int x, int y, int w, Runnable action) {
      ((Button)this.addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build())).active = !this.runtime
         .settings()
         .readOnly();
   }

   private void stage(Runnable action, String message) {
      action.run();
      this.notice = message + " Confirm below; Back cancels.";
      this.rebuild();
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      draw.fill(0, 0, this.width, this.height, -15592164);
      draw.drawCenteredString(this.font, this.getTitle(), this.width / 2, 22, (int)this.runtime.theme().accent());
      draw.drawWordWrap(
         this.font,
         Component.literal(this.runtime.settings().error().isBlank() ? this.notice : this.runtime.settings().error()),
         this.width / 2 - 130,
         176,
         260,
         -4208428,
         false
      );
      super.render(draw, mouseX, mouseY, delta);
   }

   public void onClose() {
      this.runtime.settings().cancelReset();
      this.runtime.settings().flush();
      this.minecraft.setScreen(this.parent);
   }
}
