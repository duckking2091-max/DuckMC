package io.duckmc.content;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ContentUpdates {
   private ContentUpdates() {
   }

   public static ContentUpdates.Result check(String installedFile, List<ContentDetails.File> files) {
      if (files.isEmpty()) {
         return new ContentUpdates.Result(ContentUpdates.Status.NO_COMPATIBLE_FILE, "No compatible downloadable version is listed.");
      } else {
         Optional<ContentDetails.File> installed = files.stream().filter(file -> file.id().equals(installedFile)).findFirst();
         if (installed.isEmpty()) {
            return new ContentUpdates.Result(ContentUpdates.Status.UNKNOWN, "Installed file is no longer listed. Review available versions manually.");
         } else {
            ContentDetails.File latest = files.stream().max(Comparator.comparing(ContentDetails.File::publishedAt)).orElseThrow();
            if (installed.get().publishedAt().equals(Instant.EPOCH) || latest.publishedAt().equals(Instant.EPOCH)) {
               return new ContentUpdates.Result(ContentUpdates.Status.UNKNOWN, "Release dates unavailable; cannot determine whether a version is newer.");
            } else {
               return latest.publishedAt().isAfter(installed.get().publishedAt())
                  ? new ContentUpdates.Result(
                     ContentUpdates.Status.NEWER_AVAILABLE, "Newer release available: " + latest.version() + ". Nothing was downloaded or enabled."
                  )
                  : new ContentUpdates.Result(
                     ContentUpdates.Status.CURRENT, "No newer compatible release found. Installed version: " + installed.get().version()
                  );
            }
         }
      }
   }

   public record Result(ContentUpdates.Status status, String message) {
   }

   public enum Status {
      CURRENT,
      NEWER_AVAILABLE,
      UNKNOWN,
      NO_COMPATIBLE_FILE;
   }
}
