package io.duckmc.content;

import io.duckmc.storage.ArchiveInspector;
import io.duckmc.storage.SafePaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.zip.ZipFile;

public final class WorldImporter {
   private final LongConsumer progress;

   public WorldImporter() {
      this(ignored -> {});
   }

   public WorldImporter(LongConsumer progress) {
      this.progress = Objects.requireNonNull(progress);
   }

   public Path importWorld(Path archive, Path saves, String suggestedName, CancellationToken token) throws IOException {
      token.throwIfCancelled();
      saves = saves.toAbsolutePath().normalize();
      SafePaths.resolveInside(saves, "import-check");
      Files.createDirectories(saves);
      Path staging = Files.createTempDirectory(saves, ".duckmc-import-");

      try {
         Path snapshot = staging.resolve("archive.zip");

         try (
            InputStream in = Files.newInputStream(archive);
            OutputStream out = Files.newOutputStream(snapshot, StandardOpenOption.CREATE_NEW);
         ) {
            byte[] buffer = new byte[32768];
            long bytes = 0L;

            int count;
            while ((count = in.read(buffer)) != -1) {
               token.throwIfCancelled();
               bytes += count;
               if (bytes > 536870912L) {
                  throw new IOException("Archive too large");
               }

               out.write(buffer, 0, count);
            }
         }

         ArchiveInspector.Manifest manifest = new ArchiveInspector().inspect(snapshot, token);
         List<ArchiveInspector.Entry> roots = manifest.entries()
            .stream()
            .filter(e -> !e.directory() && (e.name().equals("level.dat") || e.name().endsWith("/level.dat")))
            .toList();
         if (roots.size() != 1) {
            throw new IOException("Expected exactly one world root");
         }

         String root = roots.getFirst().name();
         root = root.substring(0, root.length() - "level.dat".length());
         Path extracted = Files.createDirectory(staging.resolve("world"));
         ZipFile zip = new ZipFile(snapshot.toFile());

         try {
            try (InputStream level = zip.getInputStream(zip.getEntry(root + "level.dat"))) {
               WorldNbt.validate(level, token);
            }

            long expanded = 0L;
            byte[] buffer = new byte[32768];

            for (ArchiveInspector.Entry entry : manifest.entries()) {
               token.throwIfCancelled();
               if (entry.name().startsWith(root)) {
                  String relative = entry.name().substring(root.length());
                  if (!relative.isEmpty()) {
                     Path target = SafePaths.resolveInside(extracted, relative);
                     if (entry.directory()) {
                        Files.createDirectories(target);
                     } else {
                        Files.createDirectories(target.getParent());
                        SafePaths.resolveInside(extracted, relative);
                        long bytes = 0L;

                        int count;
                        try (
                           InputStream in = zip.getInputStream(zip.getEntry(entry.name()));
                           OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW);
                        ) {
                           while ((count = in.read(buffer)) != -1) {
                              token.throwIfCancelled();
                              bytes += count;
                              expanded += count;
                              if (bytes > entry.bytes() || expanded > manifest.totalBytes()) {
                                 throw new IOException("Archive changed during import");
                              }

                              out.write(buffer, 0, count);
                              this.progress.accept(expanded);
                           }
                        }

                        if (bytes != entry.bytes()) {
                           throw new IOException("Archive changed during import");
                        }
                     }
                  }
               }
            }
         } catch (Throwable var76) {
            try {
               zip.close();
            } catch (Throwable var63) {
               var76.addSuppressed(var63);
            }

            throw var76;
         }

         zip.close();
         token.throwIfCancelled();
         String name = suggestedName.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "-").strip().replaceAll("[. ]+$", "");
         if (name.isEmpty()) {
            name = "Imported World";
         }

         if (name.length() > 60) {
            name = name.substring(0, 60);
         }

         try {
            SafePaths.resolveInside(saves, name);
         } catch (IllegalArgumentException error) {
            name = "Imported World";
         }

         for (int index = 1; index <= 10000; index++) {
            token.throwIfCancelled();
            String candidate = name + (index == 1 ? "" : " (" + index + ")");
            Path destination = SafePaths.resolveInside(saves, candidate);
            if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
               Path reservation = SafePaths.resolveInside(saves, ".duckmc-reserve-" + candidate);

               try {
                  Files.createFile(reservation);
               } catch (FileAlreadyExistsException occupied) {
                  continue;
               }

               try {
                  token.throwIfCancelled();
                  SafePaths.resolveInside(saves, candidate);

                  try {
                     return Files.move(extracted, destination);
                  } catch (FileAlreadyExistsException var71) {
                  }
               } finally {
                  Files.deleteIfExists(reservation);
               }
            }
         }

         throw new IOException("Could not reserve a unique world name");
      } catch (IllegalArgumentException error) {
         throw new IOException("Unsafe import path");
      } finally {
         deleteStaging(staging);
      }
   }

   private static void deleteStaging(Path staging) throws IOException {
      SafePaths.resolveInside(staging.getParent(), staging.getFileName().toString());
      Files.walkFileTree(staging, new SimpleFileVisitor<Path>() {
         public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
            if (error != null) {
               throw error;
            }

            Files.delete(directory);
            return FileVisitResult.CONTINUE;
         }
      });
   }
}
