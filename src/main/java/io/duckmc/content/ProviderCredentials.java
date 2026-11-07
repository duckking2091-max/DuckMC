package io.duckmc.content;

import io.duckmc.storage.SafePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ProviderCredentials {
   private final Path directory;

   public ProviderCredentials(Path directory) {
      this.directory = directory.toAbsolutePath().normalize();
   }

   public synchronized String load() {
      try {
         Path file = SafePaths.resolveInside(this.directory, "curseforge.key");
         if (!Files.exists(file)) {
            return "";
         }

         if (Files.size(file) > 4096L) {
            throw new IOException();
         }

         String value = Files.readString(file);
         validate(value);
         return value;
      } catch (IOException | IllegalArgumentException error) {
         throw new IllegalStateException("Cannot read provider credential");
      }
   }

   public synchronized void save(String value) {
      validate(value);
      Path temporary = null;

      try {
         Path file = SafePaths.resolveInside(this.directory, "curseforge.key");
         Files.createDirectories(this.directory);
         if (!value.isEmpty()) {
            temporary = Files.createTempFile(this.directory, "credential-", ".tmp");
            Files.writeString(temporary, value);
            SafePaths.resolveInside(this.directory, "curseforge.key");
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return;
         }

         Files.deleteIfExists(file);
      } catch (IOException | IllegalArgumentException error) {
         throw new IllegalStateException("Cannot save provider credential");
      } finally {
         if (temporary != null) {
            try {
               Files.deleteIfExists(temporary);
            } catch (IOException var12) {
            }
         }
      }
   }

   private static void validate(String value) {
      if (value == null || value.length() > 4096 || value.chars().anyMatch(c -> c < 33 || c > 126)) {
         throw new IllegalArgumentException("Invalid provider credential");
      }
   }
}
