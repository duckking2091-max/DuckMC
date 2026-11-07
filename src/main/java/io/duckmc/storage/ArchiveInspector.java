package io.duckmc.storage;

import io.duckmc.content.CancellationToken;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipFile.Builder;

public final class ArchiveInspector {
   private final ArchiveInspector.Limits limits;

   public ArchiveInspector() {
      this(new ArchiveInspector.Limits(536870912L, 100000, 536870912L, 2147483648L, 200.0));
   }

   public ArchiveInspector(ArchiveInspector.Limits limits) {
      this.limits = Objects.requireNonNull(limits);
   }

   public ArchiveInspector.Manifest inspect(Path archive, CancellationToken cancellation) throws IOException {
      cancellation.throwIfCancelled();
      if (Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && Files.size(archive) <= this.limits.archiveBytes()) {
         Map<String, Boolean> names = new HashMap<>();
         Set<String> neededDirectories = new HashSet<>();
         List<ArchiveInspector.Entry> manifest = new ArrayList<>();
         long total = 0L;
         Path validationRoot = archive.toAbsolutePath().getParent().resolve("duckmc-validation-" + UUID.randomUUID());
         ZipFile zip = ((Builder)ZipFile.builder().setPath(archive)).get();

         try {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            byte[] buffer = new byte[8192];

            while (entries.hasMoreElements()) {
               cancellation.throwIfCancelled();
               ZipArchiveEntry entry = entries.nextElement();
               if (manifest.size() >= this.limits.entryCount()
                  || entry.isUnixSymlink()
                  || !zip.canReadEntryData(entry)
                  || entry.getGeneralPurposeBit().usesEncryption()) {
                  throw new IOException("Unsupported or excessive archive entries");
               }

               int type = entry.getUnixMode() & 61440;
               if (type != 0 && type != 32768 && type != 16384) {
                  throw new IOException("Special archive file rejected");
               }

               String name = entry.getName();
               if (entry.isDirectory() && name.endsWith("/")) {
                  name = name.substring(0, name.length() - 1);
               }

               try {
                  SafePaths.resolveInside(validationRoot, name);
               } catch (IllegalArgumentException e) {
                  throw new IOException("Unsafe archive entry", e);
               }

               String key = collisionKey(name);
               if (names.putIfAbsent(key, entry.isDirectory()) != null) {
                  throw new IOException("Duplicate archive path");
               }

               if (!entry.isDirectory() && neededDirectories.contains(key)) {
                  throw new IOException("Archive file/directory collision");
               }

               for (int slash = key.indexOf(47); slash >= 0; slash = key.indexOf(47, slash + 1)) {
                  String parent = key.substring(0, slash);
                  if (Boolean.FALSE.equals(names.get(parent))) {
                     throw new IOException("Archive file/directory collision");
                  }

                  neededDirectories.add(parent);
               }

               long size = 0L;
               CRC32 crc = new CRC32();

               int n;
               try (InputStream in = zip.getInputStream(entry)) {
                  while ((n = in.read(buffer)) != -1) {
                     cancellation.throwIfCancelled();
                     size += n;
                     total += n;
                     crc.update(buffer, 0, n);
                     if (size > this.limits.entryBytes()
                        || total > this.limits.expandedBytes()
                        || size > Math.max(1L, entry.getCompressedSize()) * this.limits.ratio()) {
                        throw new IOException("Expanded archive exceeds limits");
                     }
                  }
               }

               if (entry.isDirectory() && size != 0L || entry.getSize() != size || entry.getCrc() != crc.getValue()) {
                  throw new IOException("Archive size or checksum mismatch");
               }

               manifest.add(new ArchiveInspector.Entry(name, entry.isDirectory(), size));
            }
         } catch (Throwable var26) {
            if (zip != null) {
               try {
                  zip.close();
               } catch (Throwable var22) {
                  var26.addSuppressed(var22);
               }
            }

            throw var26;
         }

         if (zip != null) {
            zip.close();
         }

         if (manifest.isEmpty()) {
            throw new IOException("Archive is empty");
         } else {
            return new ArchiveInspector.Manifest(manifest, total);
         }
      } else {
         throw new IOException("Archive exceeds limits or is not a regular file");
      }
   }

   private static String collisionKey(String name) {
      String normalized = Normalizer.normalize(name, Form.NFC);
      String folded = normalized.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
      return Normalizer.normalize(folded, Form.NFC);
   }

   public record Entry(String name, boolean directory, long bytes) {
   }

   public record Limits(long archiveBytes, int entryCount, long entryBytes, long expandedBytes, double ratio) {
      public Limits(long archiveBytes, int entryCount, long entryBytes, long expandedBytes, double ratio) {
         if (archiveBytes > 0L && entryCount > 0 && entryBytes > 0L && expandedBytes > 0L && Double.isFinite(ratio) && !(ratio < 1.0)) {
            this.archiveBytes = archiveBytes;
            this.entryCount = entryCount;
            this.entryBytes = entryBytes;
            this.expandedBytes = expandedBytes;
            this.ratio = ratio;
         } else {
            throw new IllegalArgumentException("Invalid archive limits");
         }
      }
   }

   public record Manifest(List<ArchiveInspector.Entry> entries, long totalBytes) {
      public Manifest {
         entries = List.copyOf(entries);
      }
   }
}
