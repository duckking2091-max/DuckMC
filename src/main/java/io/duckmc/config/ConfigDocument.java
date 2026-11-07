package io.duckmc.config;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ConfigDocument(int schemaVersion, String activeProfile, Map<String, JsonObject> profiles, boolean advantagesAcknowledged) {
   public ConfigDocument {
      profiles = copy(profiles);
   }

   private static Map<String, JsonObject> copy(Map<String, JsonObject> source) {
      Map<String, JsonObject> result = new LinkedHashMap<>();
      source.forEach((k, v) -> result.put(k, v.deepCopy()));
      return Collections.unmodifiableMap(result);
   }

   public Map<String, JsonObject> profiles() {
      return copy(this.profiles);
   }

   public JsonObject activeSettings() {
      return this.profiles.get(this.activeProfile).deepCopy();
   }

   public static ConfigDocument defaults() {
      return new ConfigDocument(1, "Default", Map.of("Default", SettingsSchema.defaults()), false);
   }

   public ConfigDocument withActiveSettings(JsonObject settings) {
      Map<String, JsonObject> p = this.profiles();
      LinkedHashMap<String, JsonObject> next = new LinkedHashMap<>(p);
      next.put(this.activeProfile, SettingsSchema.validate(settings));
      return new ConfigDocument(1, this.activeProfile, next, this.advantagesAcknowledged);
   }
}
