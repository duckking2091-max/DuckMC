package io.duckmc.ui.widget;

import com.mojang.blaze3d.platform.InputConstants.Type;
import io.duckmc.DuckMcRuntime;
import io.duckmc.input.KeyBindingSpec;
import io.duckmc.input.KeybindService;
import io.duckmc.ui.render.NeonUi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class KeyCapture {
   private final DuckMcRuntime runtime;
   private final Runnable rebuild;
   private String action = "";
   private int pendingKey = -2;
   private int pendingModifiers;
   private String notice = "";
   private Button button;

   public KeyCapture(DuckMcRuntime runtime, Runnable rebuild) {
      this.runtime = runtime;
      this.rebuild = rebuild;
   }

   public boolean active() {
      return !this.action.isEmpty();
   }

   public String notice() {
      return this.notice;
   }

   public void begin(String next) {
      this.action = next;
      this.pendingKey = -2;
      this.notice = "Press a key. Escape cancels; Backspace or Delete clears.";
      this.rebuild.run();
   }

   public void cancel() {
      if (this.active()) {
         this.action = "";
         this.notice = "";
         this.rebuild.run();
      }
   }

   public Button track(String id, Button widget) {
      if (id.equals(this.action)) {
         this.button = widget;
      }

      return widget;
   }

   public String label(String id) {
      if (id.equals(this.action)) {
         return "Press a key...";
      }

      KeyBindingSpec binding = new KeybindService(this.runtime.config()).binding(id);
      return binding.unbound() ? "Key" : name(binding.key(), binding.modifiers());
   }

   private static String name(int key, int modifiers) {
      StringBuilder parts = new StringBuilder();
      if ((modifiers & 2) != 0) {
         parts.append("Ctrl+");
      }

      if ((modifiers & 1) != 0) {
         parts.append("Shift+");
      }

      if ((modifiers & 4) != 0) {
         parts.append("Alt+");
      }

      if ((modifiers & 8) != 0) {
         parts.append("Super+");
      }

      return parts + Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
   }

   private List<KeyBindingSpec> duckConflicts(int key, int modifiers) {
      KeybindService keys = new KeybindService(this.runtime.config());
      return KeybindService.actions()
         .stream()
         .filter(other -> !other.equals(this.action))
         .<KeyBindingSpec>map(keys::binding)
         .filter(b -> b.key() == key && b.modifiers() == modifiers)
         .toList();
   }

   private List<String> minecraftConflicts(int key, int modifiers) {
      Minecraft client = Minecraft.getInstance();
      return Arrays.stream(client.options.keyMappings)
         .filter(b -> !b.getName().startsWith("key.duckmc.") && b.matches(new KeyEvent(key, 0, modifiers)))
         .map(b -> Component.translatable(b.getName()).getString())
         .toList();
   }

   private void assign(int key, int modifiers) {
      for (KeyBindingSpec conflict : this.duckConflicts(key, modifiers)) {
         this.runtime.settings().bind(conflict.action(), -1, 0);
      }

      this.runtime.settings().bind(this.action, key, modifiers);
      this.action = "";
      this.notice = "";
      this.rebuild.run();
   }

   public boolean key(KeyEvent input) {
      if (!this.active()) {
         return false;
      }

      int key = input.key();
      if (key == 256) {
         this.cancel();
      } else if (key == 259 || key == 261) {
         this.assign(-1, 0);
      } else if (key >= 32 && key <= 348 && (key < 340 || key > 347)) {
         int modifiers = input.modifiers() & 15;
         ArrayList<String> uses = new ArrayList<>();
         this.duckConflicts(key, modifiers).forEach(b -> uses.add("DuckMC " + b.action().replace('_', ' ')));
         uses.addAll(this.minecraftConflicts(key, modifiers));
         if (!uses.isEmpty() && (key != this.pendingKey || modifiers != this.pendingModifiers)) {
            this.pendingKey = key;
            this.pendingModifiers = modifiers;
            this.notice = name(key, modifiers) + " is used by " + String.join(", ", uses) + ". Press again to replace.";
         } else {
            this.assign(key, modifiers);
         }
      }

      return true;
   }

   public void renderPulse(GuiGraphics draw, int accent) {
      if (this.active() && this.button != null && this.button.visible) {
         double wave = (Math.sin(System.nanoTime() / 1.8E8) + 1.0) / 2.0;
         NeonUi.glow(draw, this.button.getX(), this.button.getY(), this.button.getWidth(), this.button.getHeight(), accent, 0.35F + (float)wave * 0.65F);
      }
   }
}
