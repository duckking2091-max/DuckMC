package io.duckmc.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class CurseForgeProvider implements ContentCatalog {
   private static final String API = "https://api.curseforge.com/v1/";
   private final ProviderHttp http;
   private final Supplier<String> key;

   public CurseForgeProvider(ProviderHttp http, Supplier<String> key) {
      this.http = http;
      this.key = key;
   }

   private static boolean subcategoryOf(JsonObject o, int contentClass) {
      boolean isClass = o.has("isClass") && !o.get("isClass").isJsonNull() && o.get("isClass").getAsBoolean();
      return !isClass && o.has("classId") && !o.get("classId").isJsonNull() && o.get("classId").getAsInt() == contentClass;
   }

   private CompletableFuture<String> get(URI uri, CancellationToken token) {
      String value;
      try {
         value = this.key.get();
      } catch (RuntimeException error) {
         return CompletableFuture.failedFuture(new ProviderException(ProviderException.Kind.AUTH_REQUIRED));
      }

      return value != null && !value.isBlank()
         ? this.http.get(uri, Map.of("x-api-key", value), token)
         : CompletableFuture.failedFuture(new ProviderException(ProviderException.Kind.AUTH_REQUIRED));
   }

   private CompletableFuture<CurseForgeProvider.Catalog> catalog(ContentResult.Kind kind, CancellationToken token) {
      return ProviderJson.<Integer>map(this.get(URI.create("https://api.curseforge.com/v1/games?pageSize=50"), token), json -> {
            for (JsonElement entry : json.getAsJsonObject().getAsJsonArray("data")) {
               JsonObject game = entry.getAsJsonObject();
               if (ProviderJson.text(game, "slug").equals("minecraft")) {
                  return game.get("id").getAsInt();
               }
            }

            throw new ProviderException(ProviderException.Kind.UNSUPPORTED);
         })
         .thenCompose(
            game -> ProviderJson.map(
               this.get(ProviderJson.uri("https://api.curseforge.com/v1/categories", Map.of("gameId", game.toString())), token), json -> {
                  JsonArray categories = json.getAsJsonObject().getAsJsonArray("data");

                  for (JsonElement entry : categories) {
                     JsonObject o = entry.getAsJsonObject();
                     if (o.has("isClass") && o.get("isClass").getAsBoolean()) {
                        String slug = ProviderJson.text(o, "slug");
                        if (kind == ContentResult.Kind.WORLD ? slug.equals("worlds") : Set.of("texture-packs", "resource-packs").contains(slug)) {
                           return new CurseForgeProvider.Catalog(game, o.get("id").getAsInt(), categories);
                        }
                     }
                  }

                  throw new ProviderException(ProviderException.Kind.UNSUPPORTED);
               }
            )
         );
   }

   @Override
   public CompletableFuture<List<ContentResult>> search(SearchQuery query, CancellationToken token) {
      return this.catalog(query.kind(), token).thenCompose(catalog -> {
         HashMap<String, String> params = new HashMap<>();
         params.put("gameId", String.valueOf(catalog.game()));
         params.put("classId", String.valueOf(catalog.contentClass()));
         params.put("gameVersion", query.minecraftVersion());
         params.put("searchFilter", query.text());
         params.put("index", String.valueOf(query.offset()));
         params.put("pageSize", "20");
         params.put("sortOrder", "desc");

         params.put("sortField", switch (query.sort()) {
            case "downloads" -> "6";
            case "updated" -> "3";
            case "newest" -> "11";
            case "relevance" -> "2";
            default -> throw new ProviderException(ProviderException.Kind.UNSUPPORTED);
         });
         JsonArray categories = new JsonArray();

         for (String requested : query.categories()) {
            boolean found = false;

            for (JsonElement entry : catalog.categories()) {
               JsonObject o = entry.getAsJsonObject();
               if (ProviderJson.text(o, "slug").equals(requested) && subcategoryOf(o, catalog.contentClass())) {
                  categories.add(o.get("id").getAsInt());
                  found = true;
                  break;
               }
            }

            if (!found) {
               throw new ProviderException(ProviderException.Kind.UNSUPPORTED);
            }
         }

         if (!categories.isEmpty()) {
            params.put("categoryIds", categories.toString());
         }

         return ProviderJson.map(this.get(ProviderJson.uri("https://api.curseforge.com/v1/mods/search", params), token), json -> {
            ArrayList<ContentResult> results = new ArrayList<>();

            for (JsonElement entryx : json.getAsJsonObject().getAsJsonArray("data")) {
               results.add(result(entryx.getAsJsonObject(), query.kind()));
            }

            return List.copyOf(results);
         });
      });
   }

   private static ContentResult result(JsonObject o, ContentResult.Kind kind) {
      ArrayList<String> authors = new ArrayList<>();

      for (JsonElement entry : o.getAsJsonArray("authors")) {
         authors.add(ProviderJson.text(entry.getAsJsonObject(), "name"));
      }

      TreeSet<String> versions = new TreeSet<>();
      if (o.has("latestFiles")) {
         for (JsonElement entry : o.getAsJsonArray("latestFiles")) {
            versions.addAll(ProviderJson.strings(entry.getAsJsonObject(), "gameVersions"));
         }
      }

      return new ContentResult(
         o.get("id").getAsString(),
         "CurseForge",
         kind,
         ProviderJson.text(o, "name"),
         authors.isEmpty() ? "Unknown" : String.join(", ", authors),
         URI.create(ProviderJson.text(o.getAsJsonObject("links"), "websiteUrl")),
         List.copyOf(versions),
         OptionalLong.empty(),
         Optional.empty(),
         previews(o)
      );
   }

   private static List<URI> previews(JsonObject o) {
      ArrayList<URI> images = new ArrayList<>();
      if (o.has("logo") && o.get("logo").isJsonObject()) {
         JsonObject logo = o.getAsJsonObject("logo");

         for (String field : List.of("thumbnailUrl", "url")) {
            if (logo.has(field) && !logo.get(field).isJsonNull() && !ProviderJson.text(logo, field).isBlank()) {
               images.add(URI.create(ProviderJson.text(logo, field)));
            }
         }
      }

      images.addAll(gallery(o));
      return images;
   }

   private static List<URI> gallery(JsonObject o) {
      ArrayList<URI> images = new ArrayList<>();
      if (o.has("screenshots")) {
         for (JsonElement entry : o.getAsJsonArray("screenshots")) {
            images.add(URI.create(ProviderJson.text(entry.getAsJsonObject(), "url")));
         }
      }

      return images;
   }

   @Override
   public CompletableFuture<ContentDetails> details(ContentResult result, String version, CancellationToken token) {
      if (result.provider().equals("CurseForge") && result.id().matches("[0-9]{1,12}")) {
         String project = "https://api.curseforge.com/v1/mods/" + result.id();
         return ProviderJson.<JsonObject>map(this.get(URI.create(project), token), json -> {
               JsonObject o = json.getAsJsonObject().getAsJsonObject("data");
               if (o.has("allowModDistribution") && !o.get("allowModDistribution").isJsonNull() && !o.get("allowModDistribution").getAsBoolean()) {
                  throw new ProviderException(ProviderException.Kind.UNAVAILABLE);
               } else {
                  return o;
               }
            })
            .thenCompose(
               metadata -> ProviderJson.map(
                  this.get(ProviderJson.uri(project + "/files", Map.of("gameVersion", version, "pageSize", "50")), token),
                  json -> {
                     ArrayList<ContentDetails.File> files = new ArrayList<>();

                     for (JsonElement entry : json.getAsJsonObject().getAsJsonArray("data")) {
                        JsonObject file = entry.getAsJsonObject();
                        if (file.get("isAvailable").getAsBoolean()
                           && ProviderJson.strings(file, "gameVersions").contains(version)
                           && file.has("downloadUrl")
                           && !file.get("downloadUrl").isJsonNull()) {
                           HashMap<String, String> hashes = new HashMap<>();

                           for (JsonElement hash : file.getAsJsonArray("hashes")) {
                              JsonObject h = hash.getAsJsonObject();

                              hashes.put(switch (h.get("algo").getAsInt()) {
                                 case 1 -> "SHA-1";
                                 case 2 -> "MD5";
                                 default -> throw new IllegalArgumentException("Unknown hash algorithm");
                              }, ProviderJson.text(h, "value"));
                           }

                           files.add(
                              new ContentDetails.File(
                                 file.get("id").getAsString(),
                                 ProviderJson.text(file, "displayName"),
                                 ProviderJson.text(file, "fileName"),
                                 URI.create(ProviderJson.text(file, "downloadUrl")),
                                 file.get("fileLength").getAsLong(),
                                 hashes,
                                 file.has("fileDate") ? Instant.parse(ProviderJson.text(file, "fileDate")) : Instant.EPOCH
                              )
                           );
                        }
                     }

                     return new ContentDetails(result(metadata, result.kind()), ProviderJson.text(metadata, "summary"), gallery(metadata), files);
                  }
               )
            );
      } else {
         return CompletableFuture.failedFuture(new ProviderException(ProviderException.Kind.UNSUPPORTED));
      }
   }

   @Override
   public CompletableFuture<List<String>> categories(ContentResult.Kind kind, CancellationToken token) {
      return this.catalog(kind, token).thenApply(catalog -> {
         TreeSet<String> values = new TreeSet<>();

         for (JsonElement entry : catalog.categories()) {
            JsonObject o = entry.getAsJsonObject();
            if (subcategoryOf(o, catalog.contentClass())) {
               values.add(ProviderJson.text(o, "slug"));
            }
         }

         return List.copyOf(values);
      });
   }

   private record Catalog(int game, int contentClass, JsonArray categories) {
   }
}
