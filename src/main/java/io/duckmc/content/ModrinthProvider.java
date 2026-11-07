package io.duckmc.content;

import com.google.gson.Gson;
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

public final class ModrinthProvider implements ContentCatalog {
   private static final String API = "https://api.modrinth.com/v2/";
   private final ProviderHttp http;

   public ModrinthProvider(ProviderHttp http) {
      this.http = http;
   }

   @Override
   public CompletableFuture<List<ContentResult>> search(SearchQuery query, CancellationToken token) {
      if (query.kind() != ContentResult.Kind.WORLD && Set.of("relevance", "downloads", "follows", "newest", "updated").contains(query.sort())) {
         JsonArray facets = new JsonArray();

         for (String facet : List.of("project_type:resourcepack", "versions:" + query.minecraftVersion())) {
            JsonArray group = new JsonArray();
            group.add(facet);
            facets.add(group);
         }

         for (String category : query.categories()) {
            JsonArray group = new JsonArray();
            group.add("categories:" + category);
            facets.add(group);
         }

         URI uri = ProviderJson.uri(
            "https://api.modrinth.com/v2/search",
            Map.of("query", query.text(), "facets", facets.toString(), "index", query.sort(), "offset", String.valueOf(query.offset()), "limit", "20")
         );
         return ProviderJson.map(
            this.http.get(uri, Map.of(), token),
            json -> {
               ArrayList<ContentResult> results = new ArrayList<>();

               for (JsonElement element : json.getAsJsonObject().getAsJsonArray("hits")) {
                  JsonObject o = element.getAsJsonObject();
                  if (ProviderJson.text(o, "project_type").equals("resourcepack")) {
                     List<String> versions = ProviderJson.strings(o, "versions");
                     if (versions.contains(query.minecraftVersion())) {
                        ArrayList<URI> previews = new ArrayList<>();
                        if (o.has("icon_url") && !o.get("icon_url").isJsonNull() && !ProviderJson.text(o, "icon_url").isBlank()) {
                           previews.add(URI.create(ProviderJson.text(o, "icon_url")));
                        }

                        for (String image : ProviderJson.strings(o, "gallery")) {
                           previews.add(URI.create(image));
                        }

                        results.add(
                           new ContentResult(
                              ProviderJson.id(ProviderJson.text(o, "project_id")),
                              "Modrinth",
                              query.kind(),
                              ProviderJson.text(o, "title"),
                              ProviderJson.text(o, "author"),
                              URI.create("https://modrinth.com/resourcepack/" + ProviderJson.encode(ProviderJson.text(o, "slug")).replace("+", "%20")),
                              versions,
                              OptionalLong.empty(),
                              o.has("license") ? Optional.of(ProviderJson.text(o, "license")) : Optional.empty(),
                              previews
                           )
                        );
                     }
                  }
               }

               return List.copyOf(results);
            }
         );
      } else {
         return CompletableFuture.failedFuture(new ProviderException(ProviderException.Kind.UNSUPPORTED));
      }
   }

   @Override
   public CompletableFuture<ContentDetails> details(ContentResult result, String version, CancellationToken token) {
      if (result.kind() == ContentResult.Kind.RESOURCE_PACK && result.provider().equals("Modrinth")) {
         String project = "https://api.modrinth.com/v2/project/" + ProviderJson.id(result.id());
         return ProviderJson.<JsonObject>map(this.http.get(URI.create(project), Map.of(), token), JsonElement::getAsJsonObject)
            .thenCompose(
               metadata -> ProviderJson.map(
                  this.http
                     .get(
                        ProviderJson.uri(project + "/version", Map.of("game_versions", new Gson().toJson(List.of(version)), "loaders", "[\"minecraft\"]")),
                        Map.of(),
                        token
                     ),
                  json -> {
                     ArrayList<ContentDetails.File> files = new ArrayList<>();

                     for (JsonElement element : json.getAsJsonArray()) {
                        JsonObject release = element.getAsJsonObject();
                        if (ProviderJson.strings(release, "game_versions").contains(version) && ProviderJson.strings(release, "loaders").contains("minecraft")) {
                           for (JsonElement entry : release.getAsJsonArray("files")) {
                              JsonObject file = entry.getAsJsonObject();
                              HashMap<String, String> hashes = new HashMap<>();
                              file.getAsJsonObject("hashes").entrySet().forEach(e -> {
                                 hashes.put(switch ((String)e.getKey()) {
                                    case "sha1" -> "SHA-1";
                                    case "sha256" -> "SHA-256";
                                    case "sha512" -> "SHA-512";
                                    default -> (String)e.getKey();
                                 }, ((JsonElement)e.getValue()).getAsString());
                              });
                              files.add(
                                 new ContentDetails.File(
                                    ProviderJson.text(release, "id") + ":" + ProviderJson.text(file, "filename"),
                                    ProviderJson.text(release, "version_number"),
                                    ProviderJson.text(file, "filename"),
                                    URI.create(ProviderJson.text(file, "url")),
                                    file.get("size").getAsLong(),
                                    hashes,
                                    release.has("date_published") ? Instant.parse(ProviderJson.text(release, "date_published")) : Instant.EPOCH
                                 )
                              );
                           }
                        }
                     }

                     ArrayList<URI> gallery = new ArrayList<>();
                     if (metadata.has("gallery")) {
                        for (JsonElement entry : metadata.getAsJsonArray("gallery")) {
                           gallery.add(URI.create(ProviderJson.text(entry.getAsJsonObject(), "url")));
                        }
                     }

                     Optional<String> license = metadata.has("license")
                        ? Optional.of(ProviderJson.text(metadata.getAsJsonObject("license"), "id"))
                        : result.license();
                     ContentResult updated = new ContentResult(
                        result.id(),
                        result.provider(),
                        result.kind(),
                        result.title(),
                        result.author(),
                        result.source(),
                        result.versions(),
                        result.sizeBytes(),
                        license,
                        result.previews()
                     );
                     return new ContentDetails(updated, ProviderJson.text(metadata, "body"), gallery, files);
                  }
               )
            );
      } else {
         return CompletableFuture.failedFuture(new ProviderException(ProviderException.Kind.UNSUPPORTED));
      }
   }

   @Override
   public CompletableFuture<List<String>> categories(ContentResult.Kind kind, CancellationToken token) {
      return kind == ContentResult.Kind.WORLD
         ? CompletableFuture.failedFuture(new ProviderException(ProviderException.Kind.UNSUPPORTED))
         : ProviderJson.map(this.http.get(URI.create("https://api.modrinth.com/v2/tag/category"), Map.of(), token), json -> {
            TreeSet<String> results = new TreeSet<>();

            for (JsonElement entry : json.getAsJsonArray()) {
               JsonObject o = entry.getAsJsonObject();
               if (ProviderJson.text(o, "project_type").equals("resourcepack")) {
                  results.add(ProviderJson.text(o, "name"));
               }
            }

            return List.copyOf(results);
         });
   }
}
