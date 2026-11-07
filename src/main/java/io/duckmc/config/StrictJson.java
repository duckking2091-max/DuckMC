package io.duckmc.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

public final class StrictJson {
   private StrictJson() {
   }

   public static JsonObject object(String text) {
      if (text.length() <= 1048576 && text.getBytes(StandardCharsets.UTF_8).length <= 1048576) {
         try {
            JsonReader r = new JsonReader(new StringReader(text));

            JsonObject var3;
            try {
               r.setStrictness(Strictness.STRICT);
               JsonElement value = read(r, 0);
               if (r.peek() != JsonToken.END_DOCUMENT || !value.isJsonObject()) {
                  throw new IllegalArgumentException("Expected one JSON object");
               }

               var3 = value.getAsJsonObject();
            } catch (Throwable var5) {
               try {
                  r.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }

               throw var5;
            }

            r.close();
            return var3;
         } catch (IOException | NumberFormatException e) {
            throw new IllegalArgumentException("Malformed JSON", e);
         }
      } else {
         throw new IllegalArgumentException("JSON exceeds 1 MiB");
      }
   }

   private static JsonElement read(JsonReader r, int depth) throws IOException {
      if (depth > 16) {
         throw new IllegalArgumentException("JSON nesting limit exceeded");
      }

      return (JsonElement)(switch (r.peek()) {
         case BEGIN_OBJECT -> {
            JsonObject o = new JsonObject();
            r.beginObject();

            while (r.hasNext()) {
               String k = r.nextName();
               if (o.has(k)) {
                  throw new IllegalArgumentException("Duplicate JSON key");
               }

               o.add(k, read(r, depth + 1));
            }

            r.endObject();
            yield o;
         }
         case BEGIN_ARRAY -> {
            JsonArray a = new JsonArray();
            r.beginArray();

            while (r.hasNext()) {
               a.add(read(r, depth + 1));
            }

            r.endArray();
            yield a;
         }
         case STRING -> new JsonPrimitive(r.nextString());
         case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
         case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
         case NULL -> {
            r.nextNull();
            yield JsonNull.INSTANCE;
         }
         default -> throw new IllegalArgumentException("Unexpected JSON token");
      });
   }
}
