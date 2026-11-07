package io.duckmc.ui.screen;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants.Type;
import io.duckmc.DuckMcRuntime;
import io.duckmc.config.SettingsSchema;
import io.duckmc.input.KeyBindingSpec;
import io.duckmc.input.KeybindService;
import io.duckmc.input.KeybindService.BindingUpdate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class KeybindScreen extends Screen {
   private final Screen parent;
   private final DuckMcRuntime runtime;
   private int selected;
   private boolean capturing;
   private String notice = "Choose an action, then bind a key. Escape cancels capture.";

   public KeybindScreen(Screen parent, DuckMcRuntime runtime) {
      super(Component.literal("Key bindings"));
      this.parent = parent;
      this.runtime = runtime;
   }

   public KeybindScreen(Screen parent, DuckMcRuntime runtime, String action) {
      this(parent, runtime);
      this.selected = Math.max(0, KeybindService.actions().indexOf(action));
   }

   private String action() {
      return (String)KeybindService.actions().get(this.selected);
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   protected void init() {
      int x = this.width / 2 - 130;
      this.addRenderableWidget(Button.builder(Component.literal("Previous action"), b -> {
         this.capturing = false;
         this.selected = Math.floorMod(this.selected - 1, KeybindService.actions().size());
         this.rebuild();
      }).bounds(x, 65, 125, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Next action"), b -> {
         this.capturing = false;
         this.selected = (this.selected + 1) % KeybindService.actions().size();
         this.rebuild();
      }).bounds(x + 135, 65, 125, 20).build());
      ((Button)this.addRenderableWidget(Button.builder(Component.literal("Bind key"), b -> {
         this.capturing = true;
         this.notice = "Press a key with optional modifiers. Escape cancels.";
      }).bounds(x, 96, 80, 20).build())).active = !this.runtime.settings().readOnly();
      ((Button)this.addRenderableWidget(Button.builder(Component.literal("Unbind"), b -> this.bind(-1, 0)).bounds(x + 90, 96, 80, 20).build())).active = !this.runtime
         .settings()
         .readOnly();
      ((Button)this.addRenderableWidget(Button.builder(Component.literal("Default"), b -> {
         JsonObject defaults = SettingsSchema.defaults();
         this.bind(defaults.get("key." + this.action()).getAsInt(), defaults.get("key." + this.action() + ".modifiers").getAsInt());
      }).bounds(x + 180, 96, 80, 20).build())).active = !this.runtime.settings().readOnly();
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.back"), b -> this.onClose()).bounds(this.width / 2 - 40, this.height - 30, 80, 20).build()
      );
   }

   private void bind(int key, int modifiers) {
      BindingUpdate update = this.runtime.settings().bind(this.action(), key, modifiers);
      this.runtime.input().applySettings(this.runtime.config().activeSettings());
      this.capturing = false;
      this.notice = update.conflicts().isEmpty()
         ? "Binding saved to the active profile."
         : "Conflicts with: " + update.conflicts().stream().map(b -> b.action()).collect(Collectors.joining(", "));
      if (key >= 0 && this.minecraft.options.keyMappings != null) {
         List<String> matches = Arrays.stream(this.minecraft.options.keyMappings)
            .filter(b -> !b.getName().startsWith("key.duckmc.") && b.matches(new KeyEvent(key, 0, modifiers)))
            .map(b -> Component.translatable(b.getName()).getString())
            .toList();
         if (!matches.isEmpty()) {
            this.notice = this.notice + " Minecraft key overlap: " + String.join(", ", matches);
         }
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (!this.capturing) {
         return super.keyPressed(input);
      }

      if (input.key() == 256) {
         this.capturing = false;
         this.notice = "Capture cancelled.";
         return true;
      }

      if (input.key() >= 340 && input.key() <= 347) {
         return true;
      }

      this.bind(input.key(), input.modifiers() & 15);
      return true;
   }

   public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      draw.fill(0, 0, this.width, this.height, -15592164);
      KeyBindingSpec binding = new KeybindService(this.runtime.config()).binding(this.action());
      String key = binding.unbound() ? "Unbound" : Type.KEYSYM.getOrCreate(binding.key()).getDisplayName().getString();
      draw.drawCenteredString(
         this.font,
         this.action().replace('_', ' ') + " : " + key + " (modifiers " + binding.modifiers() + ")",
         this.width / 2,
         28,
         (int)this.runtime.theme().accent()
      );
      draw.drawWordWrap(this.font, Component.literal(this.notice), this.width / 2 - 130, 135, 260, -4208428, false);
      draw.drawWordWrap(
         this.font,
         Component.literal("Bindings for unreleased modules stay inactive. Shift=1, Ctrl=2, Alt=4, Super=8."),
         this.width / 2 - 130,
         175,
         260,
         -7299668,
         false
      );
      super.render(draw, mouseX, mouseY, delta);
   }

   public void onClose() {
      this.capturing = false;
      this.runtime.settings().settle();
      this.minecraft.setScreen(this.parent);
   }
}
