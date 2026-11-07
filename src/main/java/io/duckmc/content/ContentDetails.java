package io.duckmc.content;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ContentDetails(ContentResult result, String description, List<URI> gallery, List<ContentDetails.File> files) {
   public ContentDetails(ContentResult result, String description, List<URI> gallery, List<ContentDetails.File> files) {
      Objects.requireNonNull(result);
      if (description.length() > 65536) {
         description = description.substring(0, 65536);
      }

      gallery = List.copyOf(gallery);
      gallery.forEach(ContentResult::checkUri);
      files = files.stream().sorted(Comparator.comparing(ContentDetails.File::publishedAt).reversed()).toList();
      if (gallery.size() <= 100 && files.size() <= 1000) {
         this.result = result;
         this.description = description;
         this.gallery = gallery;
         this.files = files;
      } else {
         throw new IllegalArgumentException("Excessive metadata");
      }
   }

   public record File(String id, String version, String filename, URI uri, long size, Map<String, String> hashes, Instant publishedAt) {
      public File(String id, String version, String filename, URI uri, long size, Map<String, String> hashes) {
         this(id, version, filename, uri, size, hashes, Instant.EPOCH);
      }

      public File(String id, String version, String filename, URI uri, long size, Map<String, String> hashes, Instant publishedAt) {
         Objects.requireNonNull(publishedAt);
         ContentResult.checkUri(uri);

         for (String value : List.of(id, version, filename)) {
            if (value.isBlank() || value.length() > 512) {
               throw new IllegalArgumentException("Invalid file metadata");
            }
         }

         if (size >= 0L && size <= 536870912L) {
            hashes = Map.copyOf(hashes);
            this.id = id;
            this.version = version;
            this.filename = filename;
            this.uri = uri;
            this.size = size;
            this.hashes = hashes;
            this.publishedAt = publishedAt;
         } else {
            throw new IllegalArgumentException("File exceeds download limit");
         }
      }
   }
}
