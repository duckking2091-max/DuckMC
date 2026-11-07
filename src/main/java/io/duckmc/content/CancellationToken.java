package io.duckmc.content;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
   private final AtomicBoolean cancelled = new AtomicBoolean();
   private final Set<CompletableFuture<?>> pending = new HashSet<>();

   public void cancel() {
      this.cancelled.set(true);
      CompletableFuture<?>[] futures;
      synchronized (this.pending) {
         futures = this.pending.toArray(CompletableFuture[]::new);
         this.pending.clear();
      }

      for (CompletableFuture<?> future : futures) {
         future.cancel(true);
      }
   }

   public <T> CompletableFuture<T> track(CompletableFuture<T> future) {
      synchronized (this.pending) {
         if (this.cancelled.get()) {
            future.cancel(true);
         } else {
            this.pending.add(future);
         }
      }

      future.whenComplete((value, error) -> {
         synchronized (this.pending) {
            this.pending.remove(future);
         }
      });
      return future;
   }

   public boolean isCancelled() {
      return this.cancelled.get();
   }

   public void throwIfCancelled() {
      if (this.isCancelled() || Thread.currentThread().isInterrupted()) {
         throw new CancellationException("Operation cancelled");
      }
   }
}
