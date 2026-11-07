package io.duckmc.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigValidator {
   private ConfigValidator() {
   }

   public static void profileName(String name) {
      if (name == null || !name.matches("[\\p{L}\\p{N} _-]{1,40}")) {
         throw new IllegalArgumentException("Invalid profile name");
      }
   }

   public static ConfigDocument validate(ConfigDocument d) {
      if (d.schemaVersion() != 1) {
         throw new IllegalArgumentException("Unsupported schema");
      } else {
         profileName(d.activeProfile());
         Map<String, JsonObject> profiles = d.profiles();
         if (!profiles.isEmpty() && profiles.size() <= 32 && profiles.containsKey(d.activeProfile())) {
            Map<String, JsonObject> validated = new LinkedHashMap<>();
            profiles.forEach((name, p) -> {
               profileName(name);
               validated.put(name, SettingsSchema.validate(p));
            });
            return new ConfigDocument(1, d.activeProfile(), validated, d.advantagesAcknowledged());
         } else {
            throw new IllegalArgumentException("Invalid profile selection");
         }
      }
   }

   static void keys(JsonObject object, Set<String> allowed) {
      for (String key : object.keySet()) {
         if (!allowed.contains(key)) {
            throw new IllegalArgumentException("Unknown field: " + key);
         }
      }
   }

   static int integer(JsonObject object, String key) {
      JsonElement v = object.get(key);
      if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
         try {
            return v.getAsBigDecimal().intValueExact();
         } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Expected integer", e);
         }
      } else {
         throw new IllegalArgumentException("Expected integer: " + key);
      }
   }

   static String string(JsonObject o, String k) {
      JsonElement v = o.get(k);
      if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
         return v.getAsString();
      } else {
         throw new IllegalArgumentException("Expected text: " + k);
      }
   }
}
