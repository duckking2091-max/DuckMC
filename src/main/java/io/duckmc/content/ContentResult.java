package io.duckmc.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.duckmc.config.StrictJson;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record ContentResult(
   String id,
   String provider,
   ContentResult.Kind kind,
   String title,
   String author,
   URI source,
   List<String> versions,
   OptionalLong sizeBytes,
   Optional<String> license,
   List<URI> previews
) {
   public ContentResult {
      for (String value : List.of(id, provider, title, author)) {
         if (value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException("Invalid content identity");
         }
      }

      Objects.requireNonNull(kind);
      checkUri(source);
      versions = List.copyOf(versions);
      previews = List.copyOf(previews);
      previews.forEach(ContentResult::checkUri);
      if (sizeBytes.isPresent() && sizeBytes.getAsLong() < 0L) {
         throw new IllegalArgumentException("Invalid content size");
      }
   }

   public static void checkUri(URI uri) {
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
         throw new IllegalArgumentException("Expected HTTPS content URI");
      }
   }

   public static ContentResult parse(String json) {
      if (json.length() > 65536) {
         throw new IllegalArgumentException("Result too large");
      }

      try {
         JsonObject o = StrictJson.object(json);
         List<String> versions = new ArrayList<>();
         if (o.has("versions")) {
            for (JsonElement e : o.getAsJsonArray("versions")) {
               if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                  throw new IllegalArgumentException("Invalid version");
               }

               versions.add(e.getAsString());
            }
         }

         List<URI> previews = new ArrayList<>();
         if (o.has("previews")) {
            for (JsonElement e : o.getAsJsonArray("previews")) {
               if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                  throw new IllegalArgumentException("Invalid preview");
               }

               previews.add(URI.create(e.getAsString()));
            }
         }

         OptionalLong size = OptionalLong.empty();
         if (o.has("sizeBytes")) {
            JsonElement n = o.get("sizeBytes");
            if (!n.isJsonPrimitive() || !n.getAsJsonPrimitive().isNumber()) {
               throw new IllegalArgumentException("Invalid size");
            }

            size = OptionalLong.of(n.getAsBigDecimal().longValueExact());
         }

         return new ContentResult(
            text(o, "id"),
            text(o, "provider"),
            ContentResult.Kind.valueOf(text(o, "kind")),
            text(o, "title"),
            text(o, "author"),
            URI.create(text(o, "source")),
            versions,
            size,
            o.has("license") ? Optional.of(text(o, "license")) : Optional.empty(),
            previews
         );
      } catch (RuntimeException e) {
         throw new IllegalArgumentException("Invalid provider result", e);
      }
   }

   private static String text(JsonObject o, String key) {
      JsonElement e = o.get(key);
      if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
         return e.getAsString();
      } else {
         throw new IllegalArgumentException("Missing text field");
      }
   }

   public enum Kind {
      WORLD,
      RESOURCE_PACK;
   }
}
