package io.duckmc.fabric;

import io.duckmc.content.CancellationToken;
import io.duckmc.content.ContentCatalog;
import io.duckmc.content.ContentResult;
import io.duckmc.content.CurseForgeProvider;
import io.duckmc.content.HttpDownloadManager;
import io.duckmc.content.ManagedPackStore;
import io.duckmc.content.ModrinthProvider;
import io.duckmc.content.ProviderCredentials;
import io.duckmc.content.ProviderHttp;
import io.duckmc.content.WorldImporter;
import io.duckmc.content.ContentDetails.File;
import io.duckmc.content.DownloadManager.Handle;
import io.duckmc.content.DownloadManager.Request;
import io.duckmc.content.ManagedPackStore.Entry;
import io.duckmc.content.ResourcePackService.Outcome;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class ContentAdapter implements AutoCloseable {
   private final Minecraft client;
   private final ProviderCredentials credentials;
   private final Map<String, ContentCatalog> providers;
   private final HttpDownloadManager downloads;
   private final ManagedPackStore store;
   private final MinecraftPackService packs;
   private final ExecutorService worker = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
      Thread thread = new Thread(runnable, "DuckMC-content");
      thread.setDaemon(true);
      return thread;
   });

   public ContentAdapter(Minecraft client, Path config) {
      this.client = client;
      this.credentials = new ProviderCredentials(config);
      ProviderHttp http = new ProviderHttp();
      this.providers = Map.of("Modrinth", new ModrinthProvider(http), "CurseForge", new CurseForgeProvider(http, this.credentials::load));
      this.downloads = new HttpDownloadManager(
         client.gameDirectory.toPath().resolve("duckmc-downloads"),
         HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30L)).followRedirects(Redirect.NEVER).build()
      );
      this.store = new ManagedPackStore(client.getResourcePackDirectory(), config);
      this.packs = new MinecraftPackService(client, this.store);
   }

   public ContentCatalog provider(String name) {
      return Objects.requireNonNull(this.providers.get(name));
   }

   public ProviderCredentials credentials() {
      return this.credentials;
   }

   public ManagedPackStore store() {
      return this.store;
   }

   public MinecraftPackService packs() {
      return this.packs;
   }

   public Handle download(File file) {
      return this.downloads.start(new Request(file.uri(), Math.max(1L, file.size()), file.hashes(), file.size()));
   }

   public Handle preview(URI uri) {
      return this.downloads.start(new Request(uri, 8388608L, Map.of()));
   }

   public <T> CompletableFuture<T> work(Supplier<T> action) {
      try {
         return CompletableFuture.supplyAsync(action, this.worker);
      } catch (RejectedExecutionException error) {
         return CompletableFuture.failedFuture(new IOException("Content queue is full or closed"));
      }
   }

   public CompletableFuture<Outcome> install(Path download, ContentResult result, File file) {
      return this.<Entry>work(() -> {
         try {
            if (Files.size(download) != file.size()) {
               throw new IOException();
            } else {
               return this.store.install(download, result, file);
            }
         } catch (IOException error) {
            throw new CompletionException(new IOException("Pack validation or installation failed."));
         }
      }).thenCompose(entry -> this.packs.enable(entry.id()));
   }

   public CompletableFuture<Path> importWorld(Path download, String name, CancellationToken token) {
      Path saves = this.client.gameDirectory.toPath().resolve("saves");
      return this.work(() -> {
         try {
            return new WorldImporter().importWorld(download, saves, name, token);
         } catch (IOException error) {
            throw new CompletionException(new IOException("World validation or import failed; existing saves were not replaced."));
         }
      });
   }

   @Override
   public void close() {
      this.downloads.close();
      this.packs.close();
      this.worker.shutdownNow();
   }
}
