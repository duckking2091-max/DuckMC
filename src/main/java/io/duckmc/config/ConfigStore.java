package io.duckmc.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class ConfigStore {
   private final Path root;
   private final Path primary;
   private final Path backup;
   private final ConfigCodec codec = new ConfigCodec();
   private boolean readOnly;

   public ConfigStore(Path root) {
      this.root = root.toAbsolutePath().normalize();
      this.primary = this.root.resolve("config.json");
      this.backup = this.root.resolve("config.backup.json");
   }

   public synchronized ConfigStore.LoadResult load() {
      this.readOnly = false;

      try {
         this.safeRoot();
         return !Files.exists(this.primary, LinkOption.NOFOLLOW_LINKS)
            ? new ConfigStore.LoadResult(ConfigDocument.defaults(), "", false)
            : new ConfigStore.LoadResult(this.read(this.primary), "", false);
      } catch (ConfigMigrator.FutureSchemaException e) {
         this.readOnly = true;
         return new ConfigStore.LoadResult(ConfigDocument.defaults(), e.getMessage(), true);
      } catch (IOException | IllegalArgumentException e) {
         try {
            return new ConfigStore.LoadResult(
               this.read(this.backup), "Configuration could not be read; recovered the last valid backup. Original preserved.", false
            );
         } catch (ConfigMigrator.FutureSchemaException futureBackup) {
            this.readOnly = true;
            return new ConfigStore.LoadResult(ConfigDocument.defaults(), futureBackup.getMessage(), true);
         } catch (IOException | IllegalArgumentException ignored) {
            return new ConfigStore.LoadResult(
               ConfigDocument.defaults(), "Configuration and backup could not be read; using defaults. Existing files preserved.", false
            );
         }
      }
   }

   public synchronized void save(ConfigDocument document) throws IOException {
      if (this.readOnly) {
         throw new IOException("Newer configuration is read-only");
      }

      String encoded = this.codec.encode(document);
      this.safeRoot();
      Files.createDirectories(this.root);
      this.safeRoot();
      if (Files.exists(this.backup, LinkOption.NOFOLLOW_LINKS)) {
         this.safeFile(this.backup);

         try {
            this.read(this.backup);
         } catch (ConfigMigrator.FutureSchemaException e) {
            this.readOnly = true;
            throw new IOException(e.getMessage(), e);
         } catch (IllegalArgumentException e) {
            Files.copy(this.backup, this.root.resolve("config.backup.corrupt-" + UUID.randomUUID() + ".json"));
         }
      }

      if (Files.exists(this.primary, LinkOption.NOFOLLOW_LINKS)) {
         this.safeFile(this.primary);

         try {
            ConfigDocument previous = this.read(this.primary);
            this.atomicWrite(this.backup, this.codec.encode(previous));
         } catch (ConfigMigrator.FutureSchemaException e) {
            this.readOnly = true;
            throw new IOException(e.getMessage(), e);
         } catch (IllegalArgumentException e) {
            Files.copy(this.primary, this.root.resolve("config.corrupt-" + UUID.randomUUID() + ".json"));
         }
      }

      this.atomicWrite(this.primary, encoded);
   }

   private ConfigDocument read(Path file) throws IOException {
      this.safeRoot();
      this.safeFile(file);
      if (Files.size(file) > 1048576L) {
         throw new IllegalArgumentException("Configuration exceeds size limit");
      }

      try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
         byte[] bytes = in.readNBytes(1048577);
         if (bytes.length > 1048576) {
            throw new IllegalArgumentException("Configuration exceeds size limit");
         }

         String text = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
         return this.codec.decode(text);
      } catch (CharacterCodingException invalidEncoding) {
         throw new IllegalArgumentException("Configuration is not valid UTF-8", invalidEncoding);
      }
   }

   private void atomicWrite(Path file, String text) throws IOException {
      this.safeFile(file);
      Path temp = Files.createTempFile(this.root, "duckmc-", ".tmp");

      try {
         try (FileChannel out = FileChannel.open(temp, StandardOpenOption.WRITE)) {
            ByteBuffer b = StandardCharsets.UTF_8.encode(text);

            while (b.hasRemaining()) {
               out.write(b);
            }

            out.force(true);
         }

         Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
         Files.deleteIfExists(temp);
      }
   }

   private void safeRoot() throws IOException {
      for (Path p = this.root; p != null; p = p.getParent()) {
         if (Files.isSymbolicLink(p)) {
            throw new IOException("Symbolic configuration paths are not supported");
         }
      }
   }

   private void safeFile(Path file) throws IOException {
      if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
         throw new IOException("Configuration path is not a regular file");
      }
   }

   public record LoadResult(ConfigDocument document, String notice, boolean readOnly) {
   }
}
