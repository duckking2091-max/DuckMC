package io.duckmc.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.duckmc.config.StrictJson;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ProviderJson {
   static JsonElement parse(String body) {
      return StrictJson.object("{\"value\":" + body + "}").get("value");
   }

   static String text(JsonObject object, String key) {
      JsonElement value = object.get(key);
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
         return value.getAsString();
      } else {
         throw new IllegalArgumentException("Missing text");
      }
   }

   static List<String> strings(JsonObject object, String key) {
      if (!object.has(key)) {
         return List.of();
      }

      ArrayList<String> result = new ArrayList<>();

      for (JsonElement value : object.getAsJsonArray(key)) {
         if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid list");
         }

         result.add(value.getAsString());
      }

      return List.copyOf(result);
   }

   static String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
   }

   static URI uri(String base, Map<String, String> params) {
      return URI.create(base + "?" + params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&")));
   }

   static <T> CompletableFuture<T> map(CompletableFuture<String> future, Function<JsonElement, T> action) {
      return future.thenApply(body -> {
         try {
            return action.apply(parse(body));
         } catch (ProviderException | CancellationException error) {
            throw error;
         } catch (RuntimeException error) {
            throw new ProviderException(ProviderException.Kind.INVALID_RESPONSE);
         }
      });
   }

   static String id(String id) {
      if (!id.matches("[a-zA-Z0-9_-]{1,128}")) {
         throw new ProviderException(ProviderException.Kind.INVALID_RESPONSE);
      } else {
         return id;
      }
   }
}
