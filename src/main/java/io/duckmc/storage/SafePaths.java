package io.duckmc.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

public final class SafePaths {
   private SafePaths() {
   }

   public static Path resolveInside(Path root, String entry) {
      if (entry != null && !entry.isEmpty() && entry.length() <= 240 && !entry.startsWith("/") && !entry.contains("\\") && !entry.contains(":")) {
         String[] parts = entry.split("/", -1);

         for (String part : parts) {
            String base = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (part.isEmpty()
               || part.equals(".")
               || part.equals("..")
               || part.endsWith(".")
               || part.endsWith(" ")
               || part.chars().anyMatch(c -> c < 32 || c == 127 || "<>\"|?*".indexOf(c) >= 0)
               || base.matches("CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]")) {
               throw new IllegalArgumentException("Unsafe archive path");
            }
         }

         Path normalized = root.toAbsolutePath().normalize();
         Path result = normalized.resolve(entry).normalize();
         if (!result.startsWith(normalized)) {
            throw new IllegalArgumentException("Archive path escapes destination");
         }

         for (Path p = result; p != null; p = p.getParent()) {
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
               try {
                  if (Files.isSymbolicLink(p) || !p.toRealPath().equals(p.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                     throw new IllegalArgumentException("Linked destination path");
                  }
               } catch (IOException e) {
                  throw new IllegalArgumentException("Cannot validate destination", e);
               }

               if (!p.equals(result) && !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                  throw new IllegalArgumentException("File used as directory");
               }
            }
         }

         return result;
      } else {
         throw new IllegalArgumentException("Unsafe archive path");
      }
   }
}
