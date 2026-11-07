package io.duckmc.performance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;

public final class PerformancePreset {
   private static final Set<String> FPS_BOOST_SETTINGS;
   private final Map<String, JsonElement> previousValues;

   private PerformancePreset(Map<String, JsonElement> previousValues) {
      LinkedHashMap<String, JsonElement> copy = new LinkedHashMap<>();
      previousValues.forEach((key, value) -> copy.put(key, value.deepCopy()));
      this.previousValues = Collections.unmodifiableMap(copy);
   }

   public static Set<String> fpsBoostSettings() {
      return FPS_BOOST_SETTINGS;
   }

   public JsonObject ownership() {
      JsonObject ownership = new JsonObject();
      this.previousValues.forEach((key, value) -> ownership.add(key, value.deepCopy()));
      return ownership;
   }

   public static PerformancePreset restore(JsonObject ownership) {
      Objects.requireNonNull(ownership, "ownership");
      LinkedHashMap<String, JsonElement> previous = new LinkedHashMap<>();

      for (Entry<String, JsonElement> entry : ownership.entrySet()) {
         if (!FPS_BOOST_SETTINGS.contains(entry.getKey())) {
            throw new IllegalArgumentException("Unknown FPS Boost ownership key: " + entry.getKey());
         }

         JsonElement value = requiredBoolean(ownership, entry.getKey());
         previous.put(entry.getKey(), value.deepCopy());
      }

      return new PerformancePreset(previous);
   }

   public static PerformancePreset.Activation enableFpsBoost(JsonObject validatedSettings) {
      JsonObject transformed = Objects.requireNonNull(validatedSettings, "validatedSettings").deepCopy();
      LinkedHashMap<String, JsonElement> previous = new LinkedHashMap<>();

      for (String key : FPS_BOOST_SETTINGS) {
         JsonElement value = requiredBoolean(transformed, key);
         if (!value.getAsBoolean()) {
            previous.put(key, value.deepCopy());
            transformed.addProperty(key, true);
         }
      }

      return new PerformancePreset.Activation(transformed, new PerformancePreset(previous));
   }

   public JsonObject disableFpsBoost(JsonObject validatedSettings) {
      JsonObject transformed = Objects.requireNonNull(validatedSettings, "validatedSettings").deepCopy();

      for (Entry<String, JsonElement> entry : this.previousValues.entrySet()) {
         JsonElement current = requiredBoolean(transformed, entry.getKey());
         if (current.getAsBoolean()) {
            transformed.add(entry.getKey(), entry.getValue().deepCopy());
         }
      }

      return transformed;
   }

   private static JsonElement requiredBoolean(JsonObject settings, String key) {
      JsonElement value = settings.get(key);
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
         return value;
      } else {
         throw new IllegalArgumentException("Expected validated boolean setting: " + key);
      }
   }

   static {
      LinkedHashSet<String> keys = new LinkedHashSet<>();
      keys.add("module.fps_boost");
      keys.add("module.frame_pacing");
      keys.add("module.background_throttling");
      keys.add("module.loading_ui");
      keys.add("module.cache_management");
      keys.add("module.entity_culling");
      keys.add("module.distant_animation");
      keys.add("module.entity_distance");
      keys.add("module.item_limits");
      keys.add("module.gui_cache");
      keys.add("module.diagnostics");
      FPS_BOOST_SETTINGS = Collections.unmodifiableSet(keys);
   }

   public record Activation(JsonObject settings, PerformancePreset preset) {
      public Activation {
         settings = Objects.requireNonNull(settings, "settings").deepCopy();
         Objects.requireNonNull(preset, "preset");
      }

      public JsonObject settings() {
         return this.settings.deepCopy();
      }
   }
}
