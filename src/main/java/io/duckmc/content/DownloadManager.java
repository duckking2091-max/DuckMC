package io.duckmc.content;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DownloadManager {
   DownloadManager.Handle start(DownloadManager.Request var1);

   interface Handle {
      DownloadManager.Progress progress();

      void cancel();

      CompletableFuture<Path> completion();
   }

   record Progress(DownloadManager.State state, long receivedBytes, long totalBytes, String message) {
   }

   record Request(URI uri, long maximumBytes, Map<String, String> hashes, long expectedBytes) {
      public Request(URI uri, long maximumBytes, Map<String, String> hashes) {
         this(uri, maximumBytes, hashes, -1L);
      }

      public Request(URI uri, long maximumBytes, Map<String, String> hashes, long expectedBytes) {
         ContentResult.checkUri(uri);
         if (maximumBytes <= 0L) {
            throw new IllegalArgumentException("Invalid download limit");
         }

         if (expectedBytes >= -1L && expectedBytes <= maximumBytes) {
            hashes = Map.copyOf(hashes);
            this.uri = uri;
            this.maximumBytes = maximumBytes;
            this.hashes = hashes;
            this.expectedBytes = expectedBytes;
         } else {
            throw new IllegalArgumentException("Invalid expected download size");
         }
      }
   }

   enum State {
      QUEUED,
      DOWNLOADING,
      VALIDATING,
      COMPLETE,
      CANCELLED,
      FAILED;
   }
}
