package io.duckmc.performance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.duckmc.config.SettingsSchema;
import io.duckmc.config.SettingsSession;
import io.duckmc.config.StrictJson;

public final class PerformanceDraft {
   private final SettingsSession live;
   private final SettingsSession draft;
   private final String profile;

   public PerformanceDraft(SettingsSession live) {
      this.live = live;
      this.profile = live.document().activeProfile();
      this.draft = new SettingsSession(live.document(), ignored -> {}, () -> 0L, live.readOnly());
   }

   public JsonObject settings() {
      return this.draft.document().activeSettings();
   }

   private static boolean performanceKey(String key) {
      return key.startsWith("performance.") || PerformancePreset.fpsBoostSettings().contains(key);
   }

   public void preview(String key, JsonElement value) {
      if (!performanceKey(key)) {
         throw new IllegalArgumentException("Not a performance setting");
      }

      this.draft.preview(key, value);
   }

   public void setEnabled(String name, boolean value) {
      String key = "module." + name;
      if (!name.equals("fps_boost")) {
         this.preview(key, new JsonPrimitive(value));
      } else {
         JsonObject current = this.settings();
         if (current.get(key).getAsBoolean() != value) {
            JsonObject transformed;
            String ownership;
            if (value) {
               PerformancePreset.Activation activation = PerformancePreset.enableFpsBoost(current);
               transformed = activation.settings();
               ownership = activation.preset().ownership().toString();
            } else {
               try {
                  transformed = PerformancePreset.restore(StrictJson.object(current.get("performance.fpsBoostOwnership").getAsString()))
                     .disableFpsBoost(current);
               } catch (IllegalArgumentException invalid) {
                  transformed = current;
               }

               transformed.addProperty(key, false);
               ownership = "{}";
            }

            for (String setting : PerformancePreset.fpsBoostSettings()) {
               this.preview(setting, transformed.get(setting));
            }

            this.preview("performance.fpsBoostOwnership", new JsonPrimitive(ownership));
         }
      }
   }

   public void defaults() {
      JsonObject defaults = SettingsSchema.defaults();

      for (String key : PerformancePreset.fpsBoostSettings()) {
         this.preview(key, defaults.get(key));
      }

      defaults.keySet().stream().filter(keyx -> keyx.startsWith("performance.")).forEach(keyx -> this.preview(keyx, defaults.get(keyx)));
   }

   public boolean save() {
      JsonObject changes = new JsonObject();
      this.settings()
         .entrySet()
         .stream()
         .filter(entry -> performanceKey((String)entry.getKey()))
         .forEach(entry -> changes.add((String)entry.getKey(), (JsonElement)entry.getValue()));
      return this.live.savePerformanceDraft(this.profile, changes);
   }
}
