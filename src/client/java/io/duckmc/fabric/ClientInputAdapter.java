package io.duckmc.fabric;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import io.duckmc.DuckMcRuntime;
import io.duckmc.advantages.AdvantagePolicy;
import io.duckmc.pvp.PvpPolicy;
import io.duckmc.ui.model.ScreenRoute;
import io.duckmc.ui.screen.ScreenRouter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.AllowKeyPress;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ClientInputAdapter {
   private static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath("duckmc", "general"));
   private final ScreenRouter router;
   private final int defaultKey;
   private int modifiers;
   private int configuredKey;
   private KeyMapping openKey;
   private final Map<String, KeyMapping> moduleKeys = new LinkedHashMap<>();
   private final Map<String, Integer> moduleModifiers = new HashMap<>();
   private JsonObject appliedBindings;

   public ClientInputAdapter(ScreenRouter router, JsonObject settings) {
      this.router = Objects.requireNonNull(router, "router");
      int configured = Objects.requireNonNull(settings, "settings").get("key.open").getAsInt();
      this.defaultKey = configured;
      this.configuredKey = configured;
      this.modifiers = settings.get("key.open.modifiers").getAsInt();
   }

   public void register(DuckMcRuntime runtime) {
      if (this.openKey == null) {
         this.openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.duckmc.open", Type.KEYSYM, this.defaultKey, CATEGORY));
         ArrayList<String> names = new ArrayList<>(PerformanceAdapter.HOOKS.keySet());
         names.add("fps_boost");
         names.addAll(PvpPolicy.MODULES);
         names.addAll(AdvantagePolicy.MODULES);

         for (String name : names) {
            this.moduleKeys.put(name, KeyBindingHelper.registerKeyBinding(new KeyMapping("key.duckmc." + name, Type.KEYSYM, -1, CATEGORY)));
         }

         ClientTickEvents.END_CLIENT_TICK
            .register(
               (EndTick)client -> {
                  this.applySettings(runtime.config().activeSettings());
                  int heldModifiers = (client.hasShiftDown() ? 1 : 0)
                     | (client.hasControlDown() ? 2 : 0)
                     | (client.hasAltDown() ? 4 : 0)
                     | (!InputConstants.isKeyDown(client.getWindow(), 343) && !InputConstants.isKeyDown(client.getWindow(), 347) ? 0 : 8);

                  while (this.openKey.consumeClick()) {
                     int held = (client.hasShiftDown() ? 1 : 0)
                        | (client.hasControlDown() ? 2 : 0)
                        | (client.hasAltDown() ? 4 : 0)
                        | (!InputConstants.isKeyDown(client.getWindow(), 343) && !InputConstants.isKeyDown(client.getWindow(), 347) ? 0 : 8);
                     if (client.screen == null && held == this.modifiers) {
                        this.router.open(ScreenRoute.MAIN);
                     }
                  }

                  for (Entry<String, KeyMapping> entry : this.moduleKeys.entrySet()) {
                     while (entry.getValue().consumeClick()) {
                        if (client.screen == null
                           && client.level != null
                           && !runtime.settings().readOnly()
                           && heldModifiers == this.moduleModifiers.get(entry.getKey())) {
                           String namex = entry.getKey();
                           boolean enabled = !runtime.config().activeSettings().get("module." + namex).getAsBoolean();
                           if (PvpPolicy.MODULES.contains(namex)) {
                              runtime.pvp().setEnabled(namex, enabled);
                           } else if (AdvantagePolicy.MODULES.contains(namex)) {
                              runtime.advantages().setEnabled(namex, enabled);
                           } else {
                              runtime.performance().setEnabled(namex, enabled);
                           }
                        }
                     }
                  }
               }
            );
         ScreenEvents.AFTER_INIT.register((AfterInit)(client, screen, width, height) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
               ScreenKeyboardEvents.allowKeyPress(screen).register((AllowKeyPress)(current, key) -> {
                  if (this.openKey.matches(key) && (key.modifiers() & 15) == this.modifiers) {
                     this.router.open(ScreenRoute.MAIN);
                     return false;
                  } else {
                     return true;
                  }
               });
            }
         });
      }
   }

   public KeyMapping openKey() {
      if (this.openKey == null) {
         throw new IllegalStateException("DuckMC key binding is not registered");
      } else {
         return this.openKey;
      }
   }

   public void applySettings(JsonObject settings) {
      if (this.openKey != null) {
         int key = settings.get("key.open").getAsInt();
         int nextModifiers = settings.get("key.open.modifiers").getAsInt();
         boolean changed = false;
         if (this.appliedBindings == null || key != this.configuredKey || this.modifiers != nextModifiers) {
            this.configuredKey = key;
            this.modifiers = nextModifiers;
            this.openKey().setKey(Type.KEYSYM.getOrCreate(key));
            this.openKey().setDown(false);

            while (this.openKey().consumeClick()) {
            }

            changed = true;
         }

         for (Entry<String, KeyMapping> entry : this.moduleKeys.entrySet()) {
            String name = entry.getKey();
            int nextKey = settings.get("key." + name).getAsInt();
            int mods = settings.get("key." + name + ".modifiers").getAsInt();
            if (this.appliedBindings == null || nextKey != this.appliedBindings.get("key." + name).getAsInt() || mods != this.moduleModifiers.get(name)) {
               entry.getValue().setKey(Type.KEYSYM.getOrCreate(nextKey));
               entry.getValue().setDown(false);

               while (entry.getValue().consumeClick()) {
               }

               changed = true;
            }

            this.moduleModifiers.put(name, mods);
         }

         if (changed) {
            KeyMapping.resetMapping();
         }

         this.appliedBindings = settings;
      }
   }

   public KeyMapping binding(String action) {
      if (action.equals("open")) {
         return this.openKey();
      } else {
         KeyMapping binding = this.moduleKeys.get(action);
         if (binding == null) {
            throw new IllegalArgumentException("No active module binding: " + action);
         } else {
            return binding;
         }
      }
   }
}
