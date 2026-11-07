package io.duckmc.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.duckmc.config.StrictJson;
import io.duckmc.storage.SafePaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

public final class ManagedPackStore {
   private final Path packs;
   private final Path config;

   public ManagedPackStore(Path packs, Path config) {
      this.packs = packs.toAbsolutePath().normalize();
      this.config = config.toAbsolutePath().normalize();
   }

   public synchronized List<ManagedPackStore.Entry> entries() throws IOException {
      Path manifest = SafePaths.resolveInside(this.config, "managed-packs.json");
      if (!Files.exists(manifest)) {
         return List.of();
      }

      if (Files.size(manifest) > 1048576L) {
         throw new IOException("Managed manifest too large");
      }

      try {
         JsonObject json = StrictJson.object(Files.readString(manifest));
         ArrayList<ManagedPackStore.Entry> entries = new ArrayList<>();
         HashSet<String> ids = new HashSet<>();

         for (JsonElement value : json.getAsJsonArray("packs")) {
            JsonObject o = value.getAsJsonObject();
            ManagedPackStore.Entry entry = new ManagedPackStore.Entry(
               ProviderJson.text(o, "id"),
               ProviderJson.text(o, "filename"),
               ProviderJson.text(o, "sha256"),
               ProviderJson.text(o, "name"),
               ProviderJson.text(o, "provider"),
               ProviderJson.text(o, "projectId"),
               ProviderJson.text(o, "fileId"),
               ProviderJson.text(o, "source")
            );
            SafePaths.resolveInside(this.packs, entry.filename());
            if (!ids.add(entry.id()) || entries.size() >= 1000) {
               throw new IllegalArgumentException();
            }

            entries.add(entry);
         }

         return List.copyOf(entries);
      } catch (RuntimeException error) {
         throw new IOException("Managed pack manifest is invalid; no files were changed");
      }
   }

   public synchronized ManagedPackStore.Entry entry(String id) throws IOException {
      return this.entries().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow(() -> new IOException("Pack is not managed by DuckMC"));
   }

   public synchronized Path verify(String id) throws IOException {
      ManagedPackStore.Entry entry = this.entry(id);
      Path file = SafePaths.resolveInside(this.packs, entry.filename());
      if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && hash(file).equals(entry.sha256())) {
         return file;
      } else {
         throw new IOException("Managed pack changed or is missing; left untouched");
      }
   }

   public synchronized ManagedPackStore.Entry install(Path archive, ContentResult result, ContentDetails.File metadata) throws IOException {
      if (result.kind() != ContentResult.Kind.RESOURCE_PACK) {
         throw new IOException("Not a resource pack");
      }

      ArrayList<ManagedPackStore.Entry> entries = new ArrayList<>(this.entries());
      SafePaths.resolveInside(this.packs, "pack-check");
      Files.createDirectories(this.packs);
      Path temporary = Files.createTempFile(this.packs, ".duckmc-stage-", ".zip");
      Path published = null;

      try {
         long count = 0L;
         byte[] buffer = new byte[32768];

         int n;
         try (
            InputStream in = Files.newInputStream(archive);
            OutputStream out = Files.newOutputStream(temporary);
         ) {
            while ((n = in.read(buffer)) != -1) {
               count += n;
               if (count > 536870912L) {
                  throw new IOException("Pack too large");
               }

               out.write(buffer, 0, n);
            }
         }

         if (!PackMetadata.read(temporary).compatible(75, 0)) {
            throw new IOException("Pack is not compatible with Minecraft 1.21.11");
         }

         String id = UUID.randomUUID().toString();
         ManagedPackStore.Entry entry = new ManagedPackStore.Entry(
            id, "duckmc-" + id + ".zip", hash(temporary), result.title(), result.provider(), result.id(), metadata.id(), result.source().toString()
         );
         published = SafePaths.resolveInside(this.packs, entry.filename());
         Files.move(temporary, published);
         entries.add(entry);

         try {
            this.save(entries);
         } catch (IOException error) {
            Files.deleteIfExists(published);
            throw error;
         }

         return entry;
      } finally {
         Files.deleteIfExists(temporary);
      }
   }

   public synchronized void delete(String id) throws IOException {
      this.prepareDelete(id).commit();
   }

   public synchronized ManagedPackStore.PreparedDeletion prepareDelete(String id) throws IOException {
      ManagedPackStore.Entry entry = this.entry(id);
      Path file = SafePaths.resolveInside(this.packs, entry.filename());
      ManagedPackStore.Stamp before = stamp(file);
      this.verify(id);
      if (!before.equals(stamp(file))) {
         throw new IOException("Pack changed during validation");
      } else {
         return new ManagedPackStore.PreparedDeletion(entry, before);
      }
   }

   private static ManagedPackStore.Stamp stamp(Path file) throws IOException {
      BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attrs.isRegularFile()) {
         throw new IOException("Pack is not a regular file");
      } else {
         return new ManagedPackStore.Stamp(attrs.fileKey(), attrs.size(), attrs.lastModifiedTime(), attrs.creationTime());
      }
   }

   private void trash(ManagedPackStore.Entry entry, Path file) throws IOException {
      ArrayList<ManagedPackStore.Entry> entries = new ArrayList<>(this.entries());
      entries.removeIf(e -> e.id().equals(entry.id()));
      Path trash = SafePaths.resolveInside(this.packs, ".duckmc-trash/" + entry.filename());
      Files.createDirectories(trash.getParent());
      Files.move(file, trash);

      try {
         this.save(entries);
      } catch (IOException error) {
         Files.move(trash, file);
         throw error;
      }
   }

   private void save(List<ManagedPackStore.Entry> entries) throws IOException {
      Path manifest = SafePaths.resolveInside(this.config, "managed-packs.json");
      Files.createDirectories(this.config);
      JsonObject json = new JsonObject();
      JsonArray array = new JsonArray();

      for (ManagedPackStore.Entry entry : entries) {
         JsonObject o = new JsonObject();
         o.addProperty("id", entry.id());
         o.addProperty("filename", entry.filename());
         o.addProperty("sha256", entry.sha256());
         o.addProperty("name", entry.name());
         o.addProperty("provider", entry.provider());
         o.addProperty("projectId", entry.projectId());
         o.addProperty("fileId", entry.fileId());
         o.addProperty("source", entry.source());
         array.add(o);
      }

      json.add("packs", array);
      Path temporary = Files.createTempFile(this.config, "managed-packs-", ".tmp");

      try {
         Files.writeString(temporary, json.toString());
         SafePaths.resolveInside(this.config, "managed-packs.json");
         Files.move(temporary, manifest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
         Files.deleteIfExists(temporary);
      }
   }

   private static String hash(Path file) throws IOException {
      if (Files.size(file) > 536870912L) {
         throw new IOException("Pack too large");
      }

      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");

         try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[32768];
            long total = 0L;

            int n;
            while ((n = in.read(buffer)) != -1) {
               total += n;
               if (total > 536870912L) {
                  throw new IOException();
               }

               digest.update(buffer, 0, n);
            }
         }

         return HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException error) {
         throw new AssertionError(error);
      }
   }

   public record Entry(String id, String filename, String sha256, String name, String provider, String projectId, String fileId, String source) {
      public Entry {
         if (id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            && filename.equals("duckmc-" + id + ".zip")
            && sha256.matches("[0-9a-f]{64}")) {
            for (String value : List.of(name, provider, projectId, fileId, source)) {
               if (value.isBlank() || value.length() > 512) {
                  throw new IllegalArgumentException("Invalid managed metadata");
               }
            }

            if (!Set.of("Modrinth", "CurseForge").contains(provider)) {
               throw new IllegalArgumentException("Invalid provider");
            }

            ContentResult.checkUri(URI.create(source));
         } else {
            throw new IllegalArgumentException("Invalid managed identity");
         }
      }

      public String packId() {
         return "file/" + this.filename;
      }

      public ContentResult result() {
         return new ContentResult(
            this.projectId,
            this.provider,
            ContentResult.Kind.RESOURCE_PACK,
            this.name,
            "Unknown",
            URI.create(this.source),
            List.of(),
            OptionalLong.empty(),
            Optional.empty(),
            List.of()
         );
      }
   }

   public final class PreparedDeletion {
      private final ManagedPackStore.Entry expected;
      private final ManagedPackStore.Stamp expectedStamp;
      private boolean committed;

      private PreparedDeletion(ManagedPackStore.Entry expected, ManagedPackStore.Stamp expectedStamp) {
         this.expected = expected;
         this.expectedStamp = expectedStamp;
      }

      public String packId() {
         return this.expected.packId();
      }

      public void commit() throws IOException {
         synchronized (ManagedPackStore.this) {
            if (!this.committed && ManagedPackStore.this.entry(this.expected.id()).equals(this.expected)) {
               Path file = SafePaths.resolveInside(ManagedPackStore.this.packs, this.expected.filename());
               if (!this.expectedStamp.equals(ManagedPackStore.stamp(file))) {
                  throw new IOException("Managed pack changed");
               }

               ManagedPackStore.this.trash(this.expected, file);
               this.committed = true;
            } else {
               throw new IOException("Managed identity changed");
            }
         }
      }
   }

   private record Stamp(Object key, long size, FileTime modified, FileTime created) {
   }
}
