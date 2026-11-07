package io.duckmc.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Set;

public final class ProfileService {
   public ProfileService.Candidate importProfile(String text) {
      JsonObject o = StrictJson.object(text);
      ConfigValidator.keys(o, Set.of("schemaVersion", "name", "settings"));
      if (ConfigValidator.integer(o, "schemaVersion") != 1) {
         throw new IllegalArgumentException("Unsupported profile version");
      } else {
         String name = ConfigValidator.string(o, "name");
         ConfigValidator.profileName(name);
         if (o.has("settings") && o.get("settings").isJsonObject()) {
            JsonObject settings = SettingsSchema.validate(ConfigMigrator.normalizeLegacySettings(o.getAsJsonObject("settings")));
            SettingsSchema.ADVANTAGES.forEach(id -> settings.addProperty("module." + id, false));
            return new ProfileService.Candidate(name, settings);
         } else {
            throw new IllegalArgumentException("Missing profile settings");
         }
      }
   }

   public String exportProfile(String name, JsonObject settings) {
      ConfigValidator.profileName(name);
      JsonObject o = new JsonObject();
      o.addProperty("schemaVersion", 1);
      o.addProperty("name", name);
      o.add("settings", SettingsSchema.validate(settings));
      return new GsonBuilder().setPrettyPrinting().create().toJson(o);
   }

   public record Candidate(String name, JsonObject settings) {
      public Candidate {
         settings = settings.deepCopy();
      }

      public JsonObject settings() {
         return this.settings.deepCopy();
      }
   }
}
