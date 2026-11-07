package io.duckmc.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

public final class ConfigMigrator {
   public JsonObject migrate(JsonObject source) {
      int version = ConfigValidator.integer(source, "schemaVersion");
      if (version > 1) {
         throw new ConfigMigrator.FutureSchemaException();
      } else if (version == 1) {
         return this.normalizeProfiles(source.deepCopy());
      } else if (version != 0) {
         throw new IllegalArgumentException("Unsupported schema");
      } else {
         ConfigValidator.keys(source, Set.of("schemaVersion", "settings"));
         if (source.has("settings") && source.get("settings").isJsonObject()) {
            JsonObject o = new JsonObject();
            o.addProperty("schemaVersion", 1);
            o.addProperty("activeProfile", "Default");
            o.addProperty("advantagesAcknowledged", false);
            JsonObject profiles = new JsonObject();
            profiles.add("Default", source.get("settings").deepCopy());
            o.add("profiles", profiles);
            return this.normalizeProfiles(o);
         } else {
            throw new IllegalArgumentException("Missing old settings");
         }
      }
   }

   private JsonObject normalizeProfiles(JsonObject document) {
      if (document.has("profiles") && document.get("profiles").isJsonObject()) {
         JsonObject profiles = document.getAsJsonObject("profiles");

         for (String name : List.copyOf(profiles.keySet())) {
            if (profiles.get(name).isJsonObject()) {
               profiles.add(name, normalizeLegacySettings(profiles.getAsJsonObject(name)));
            }
         }
      }

      return document;
   }

   static JsonObject normalizeLegacySettings(JsonObject source) {
      JsonObject copy = source.deepCopy();
      JsonElement old = copy.get("advantages.anchor_assist.stepTicks");
      if (old != null && old.isJsonPrimitive() && old.getAsJsonPrimitive().isNumber() && old.getAsDouble() == 5.0) {
         copy.addProperty("advantages.anchor_assist.stepTicks", 4);
      }

      return copy;
   }

   public static final class FutureSchemaException extends IllegalArgumentException {
      public FutureSchemaException() {
         super("Configuration is from a newer DuckMC version; it is read-only");
      }
   }
}
