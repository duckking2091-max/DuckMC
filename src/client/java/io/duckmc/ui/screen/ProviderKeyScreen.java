package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
final class ProviderKeyScreen extends ContentPage {
   private String status = "Stored locally, not encrypted. Never included in profile exports.";

   ProviderKeyScreen(DuckMcRuntime runtime, Screen parent) {
      super(runtime, "CurseForge API key", parent);
   }

   protected void init() {
      this.layoutPage();
      var key = new EditBox(this.font, this.x, 80, this.w, 20, this.text("API key (hidden)")) {
         public void updateWidgetNarration(NarrationElementOutput builder) {
            builder.add(NarratedElementType.TITLE, Component.literal("API key hidden"));
         }
      };
      key.setMaxLength(4096);
      key.addFormatter((value, start) -> Component.literal("*".repeat(value.length())).getVisualOrderText());

      try {
         key.setValue(this.runtime.content().credentials().load());
      } catch (RuntimeException error) {
         this.status = "Stored key could not be read.";
      }

      this.addRenderableWidget(key);
      this.button("Save key", this.x, 108, this.w / 2 - 2, () -> {
         try {
            this.runtime.content().credentials().save(key.getValue());
            if (this.parent instanceof ContentScreen browser) {
               browser.credentialsChanged();
            }

            this.status = "Key saved locally. Return to browse content.";
         } catch (RuntimeException error) {
            this.status = "Key could not be saved. Check its format and file permissions.";
         }
      });
      this.button("Remove key", this.x + this.w / 2 + 2, 108, this.w / 2 - 2, () -> {
         try {
            this.runtime.content().credentials().save("");
            if (this.parent instanceof ContentScreen browser) {
               browser.credentialsChanged();
            }

            key.setValue("");
            this.status = "Stored key removed.";
         } catch (RuntimeException error) {
            this.status = "Key could not be removed.";
         }
      });
      this.button("Back", this.x, this.height - 26, this.w, this::onClose);
   }

   @Override
   protected void renderBody(GuiGraphics draw) {
      this.line(draw, "User-provided CurseForge credentials only.", 50);
      draw.drawWordWrap(this.font, this.text(this.status), this.x + 6, 140, this.w - 12, -3351058, false);
   }
}
