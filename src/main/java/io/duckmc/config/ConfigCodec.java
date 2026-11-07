package io.duckmc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigCodec {
   public static final int MAX_BYTES = 1048576;
   private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

   public ConfigDocument decode(String text) {
      JsonObject o = new ConfigMigrator().migrate(StrictJson.object(text));
      ConfigValidator.keys(o, Set.of("schemaVersion", "activeProfile", "profiles", "advantagesAcknowledged"));
      if (o.has("profiles") && o.get("profiles").isJsonObject()) {
         Map<String, JsonObject> p = new LinkedHashMap<>();
         o.getAsJsonObject("profiles").entrySet().forEach(e -> {
            if (!((JsonElement)e.getValue()).isJsonObject()) {
               throw new IllegalArgumentException("Profile must be an object");
            }

            p.put((String)e.getKey(), ((JsonElement)e.getValue()).getAsJsonObject());
         });
         JsonElement ack = o.get("advantagesAcknowledged");
         if (ack == null || ack.isJsonPrimitive() && ack.getAsJsonPrimitive().isBoolean()) {
            return ConfigValidator.validate(new ConfigDocument(1, ConfigValidator.string(o, "activeProfile"), p, ack != null && ack.getAsBoolean()));
         } else {
            throw new IllegalArgumentException("Invalid acknowledgement");
         }
      } else {
         throw new IllegalArgumentException("Missing profiles");
      }
   }

   public String encode(ConfigDocument document) {
      ConfigDocument d = ConfigValidator.validate(document);
      JsonObject o = new JsonObject();
      o.addProperty("schemaVersion", d.schemaVersion());
      o.addProperty("activeProfile", d.activeProfile());
      JsonObject p = new JsonObject();
      d.profiles().forEach(p::add);
      o.add("profiles", p);
      o.addProperty("advantagesAcknowledged", d.advantagesAcknowledged());
      String text = this.gson.toJson(o);
      if (text.getBytes(StandardCharsets.UTF_8).length > 1048576) {
         throw new IllegalArgumentException("Config too large");
      } else {
         return text;
      }
   }
}
