package io.duckmc.content;

import io.duckmc.storage.SafePaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class HttpDownloadManager implements DownloadManager, AutoCloseable {
   private final Path directory;
   private final HttpDownloadManager.StreamTransport transport;
   private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
      Thread thread = new Thread(runnable, "DuckMC-download");
      thread.setDaemon(true);
      return thread;
   });
   private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, runnable -> {
      Thread thread = new Thread(runnable, "DuckMC-download-timeout");
      thread.setDaemon(true);
      return thread;
   });
   private final Set<HttpDownloadManager.Job> jobs = ConcurrentHashMap.newKeySet();

   public HttpDownloadManager(Path directory, HttpClient client) {
      this(directory, network(client));
   }

   public HttpDownloadManager(Path directory, HttpDownloadManager.StreamTransport transport) {
      this.directory = directory.toAbsolutePath().normalize();
      this.transport = Objects.requireNonNull(transport);
      this.timer.setRemoveOnCancelPolicy(true);
   }

   @Override
   public DownloadManager.Handle start(DownloadManager.Request request) {
      HttpDownloadManager.Job job = new HttpDownloadManager.Job(request);
      this.jobs.add(job);

      try {
         job.task = this.workers.submit(job);
      } catch (RejectedExecutionException error) {
         job.fail("Download queue is full or closed.");
      }

      return job;
   }

   private static HttpDownloadManager.StreamTransport network(HttpClient client) {
      if (client.followRedirects() != Redirect.NEVER) {
         throw new IllegalArgumentException("Automatic redirects must be disabled");
      } else {
         return (uri, token) -> {
            ContentResult.checkUri(uri);
            if (uri.getPort() != -1 && uri.getPort() != 443) {
               throw new IOException();
            }

            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
               if (!publicAddress(address)) {
                  throw new IOException();
               }
            }

            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30L)).header("User-Agent", "DuckMC/0.4.0").GET().build();
            CompletableFuture<HttpResponse<InputStream>> future = token.track(client.sendAsync(request, BodyHandlers.ofInputStream()));

            HttpResponse<InputStream> response;
            try {
               response = future.get(30L, TimeUnit.SECONDS);
            } catch (Exception error) {
               future.cancel(true);
               throw error;
            }

            HashMap<String, String> headers = new HashMap<>();
            response.headers().map().forEach((key, values) -> {
               if (!values.isEmpty()) {
                  headers.put(key, values.getFirst());
               }
            });
            return new HttpDownloadManager.Transfer(response.statusCode(), headers, response.body());
         };
      }
   }

   static boolean publicAddress(InetAddress address) {
      if (!address.isAnyLocalAddress()
         && !address.isLoopbackAddress()
         && !address.isLinkLocalAddress()
         && !address.isSiteLocalAddress()
         && !address.isMulticastAddress()) {
         byte[] bytes = address.getAddress();
         if (bytes.length == 16) {
            return (bytes[0] & 254) != 252;
         }

         int first = Byte.toUnsignedInt(bytes[0]);
         int second = Byte.toUnsignedInt(bytes[1]);
         return first != 0 && first < 224 && (first != 100 || second < 64 || second > 127);
      } else {
         return false;
      }
   }

   @Override
   public void close() {
      for (HttpDownloadManager.Job job : List.copyOf(this.jobs)) {
         job.cancel();
      }

      this.workers.shutdownNow();
      this.timer.shutdownNow();

      try {
         this.workers.awaitTermination(5L, TimeUnit.SECONDS);
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
      }
   }

   private final class Job implements DownloadManager.Handle, Runnable {
      private final DownloadManager.Request request;
      private final CancellationToken token = new CancellationToken();
      private final CompletableFuture<Path> completion = new CompletableFuture<>();
      private volatile DownloadManager.Progress progress = new DownloadManager.Progress(DownloadManager.State.QUEUED, 0L, -1L, "Queued");
      private volatile Future<?> task;
      private volatile InputStream input;
      private volatile boolean timeout;
      private Path partial;

      Job(DownloadManager.Request request) {
         this.request = request;
      }

      @Override
      public DownloadManager.Progress progress() {
         return this.progress;
      }

      @Override
      public CompletableFuture<Path> completion() {
         return this.completion;
      }

      @Override
      public synchronized void cancel() {
         if (!this.completion.isDone()) {
            this.token.cancel();
            this.closeInput();
            if (this.task != null) {
               this.task.cancel(true);
            }

            this.progress = new DownloadManager.Progress(
               DownloadManager.State.CANCELLED, this.progress.receivedBytes(), this.progress.totalBytes(), "Cancelled"
            );
            this.completion.cancel(false);
            HttpDownloadManager.this.jobs.remove(this);
         }
      }

      private void closeInput() {
         InputStream current = this.input;
         if (current != null) {
            try {
               current.close();
            } catch (IOException var3) {
            }
         }
      }

      private synchronized void update(DownloadManager.State state, long received, long total, String message) {
         if (!this.completion.isDone()) {
            this.progress = new DownloadManager.Progress(state, received, total, message);
         }
      }

      private synchronized void fail(String message) {
         if (!this.completion.isDone()) {
            this.progress = new DownloadManager.Progress(DownloadManager.State.FAILED, this.progress.receivedBytes(), this.progress.totalBytes(), message);
            this.completion.completeExceptionally(new IOException(message));
            HttpDownloadManager.this.jobs.remove(this);
         }
      }

      @Override
      public void run() {
         Path published = null;
         String failure = null;

         try {
            this.token.throwIfCancelled();
            if (this.request.maximumBytes() > 536870912L) {
               throw new IOException();
            }

            Map<String, MessageDigest> digests = new HashMap<>();

            for (Entry<String, String> entry : this.request.hashes().entrySet()) {
               if (!Set.of("SHA-1", "SHA-256", "SHA-512", "MD5").contains(entry.getKey())) {
                  throw new IOException();
               }

               MessageDigest digest = MessageDigest.getInstance(entry.getKey());
               if (!entry.getValue().matches("(?i)[0-9a-f]{" + digest.getDigestLength() * 2 + "}")) {
                  throw new IOException();
               }

               digests.put(entry.getKey(), digest);
            }

            SafePaths.resolveInside(HttpDownloadManager.this.directory, "download-check");
            Files.createDirectories(HttpDownloadManager.this.directory);
            URI uri = this.request.uri();
            HttpDownloadManager.Transfer transfer = null;

            for (int redirects = 0; redirects <= 3; redirects++) {
               this.token.throwIfCancelled();
               ContentResult.checkUri(uri);
               transfer = HttpDownloadManager.this.transport.open(uri, this.token);
               this.input = transfer.body();
               this.token.throwIfCancelled();
               if (!Set.of(301, 302, 303, 307, 308).contains(transfer.status())) {
                  break;
               }

               try {
                  if (redirects == 3 || transfer.header("location").isBlank()) {
                     throw new IOException();
                  }

                  uri = uri.resolve(transfer.header("location"));
               } finally {
                  transfer.close();
                  this.input = null;
               }
            }

            if (transfer == null) {
               throw new IOException();
            }

            try (HttpDownloadManager.Transfer response = transfer) {
               if (response.status() != 200) {
                  throw new IOException();
               }

               String length = response.header("content-length");
               long total = length.isEmpty() ? -1L : Long.parseLong(length);
               if (total < -1L || total > this.request.maximumBytes()) {
                  throw new IOException();
               }

               if (total >= 0L && this.request.expectedBytes() >= 0L && total != this.request.expectedBytes()) {
                  throw new IOException();
               }

               this.partial = Files.createTempFile(HttpDownloadManager.this.directory, "duckmc-", ".part");
               long received = 0L;
               this.update(DownloadManager.State.DOWNLOADING, 0L, total, "Downloading");

               try (OutputStream output = Files.newOutputStream(this.partial)) {
                  byte[] buffer = new byte[32768];

                  while (true) {
                     this.token.throwIfCancelled();
                     ScheduledFuture<?> alarm = HttpDownloadManager.this.timer.schedule(() -> {
                        this.timeout = true;
                        this.closeInput();
                     }, 30L, TimeUnit.SECONDS);

                     int read;
                     try {
                        read = this.input.read(buffer);
                     } finally {
                        alarm.cancel(false);
                     }

                     if (this.timeout) {
                        throw new IOException();
                     }

                     this.token.throwIfCancelled();
                     if (read < 0) {
                        break;
                     }

                     if (received + read > this.request.maximumBytes() || total >= 0L && received + read > total) {
                        throw new IOException();
                     }

                     output.write(buffer, 0, read);
                     received += read;

                     for (MessageDigest digest : digests.values()) {
                        digest.update(buffer, 0, read);
                     }

                     this.update(DownloadManager.State.DOWNLOADING, received, total, "Downloading");
                  }
               }

               if (total >= 0L && total != received) {
                  throw new IOException();
               }

               if (this.request.expectedBytes() >= 0L && received != this.request.expectedBytes()) {
                  throw new IOException();
               }

               this.update(DownloadManager.State.VALIDATING, received, total, "Checking download");

               for (Entry<String, MessageDigest> entry : digests.entrySet()) {
                  byte[] expected = HexFormat.of().parseHex(this.request.hashes().get(entry.getKey()));
                  if (!MessageDigest.isEqual(expected, entry.getValue().digest())) {
                     throw new IOException();
                  }
               }

               synchronized (this) {
                  this.token.throwIfCancelled();
                  Path destination = SafePaths.resolveInside(HttpDownloadManager.this.directory, "duckmc-" + UUID.randomUUID() + ".zip");
                  Files.move(this.partial, destination);
                  this.partial = null;
                  published = destination;
                  this.progress = new DownloadManager.Progress(
                     DownloadManager.State.COMPLETE,
                     received,
                     total,
                     digests.isEmpty() ? "Downloaded; provider checksum unavailable." : "Downloaded; provider checksums verified."
                  );
                  this.completion.complete(destination);
               }
            }
         } catch (Exception error) {
            if (!this.token.isCancelled() && !(error instanceof CancellationException) && !(error instanceof InterruptedException)) {
               failure = this.timeout ? "Download timed out." : "Download failed validation or could not be completed.";
            } else {
               this.cancel();
            }
         } finally {
            this.closeInput();
            this.input = null;
            if (this.partial != null) {
               try {
                  Files.deleteIfExists(this.partial);
               } catch (IOException error) {
                  failure = "Download failed; an owned partial file could not be removed.";
               }
            }

            if (failure != null && published == null) {
               this.fail(failure);
            }

            HttpDownloadManager.this.jobs.remove(this);
         }
      }
   }

   @FunctionalInterface
   public interface StreamTransport {
      HttpDownloadManager.Transfer open(URI var1, CancellationToken var2) throws Exception;
   }

   public record Transfer(int status, Map<String, String> headers, InputStream body) implements AutoCloseable {
      @Override
      public void close() throws IOException {
         this.body.close();
      }

      String header(String name) {
         return this.headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Entry::getValue).findFirst().orElse("");
      }
   }
}
