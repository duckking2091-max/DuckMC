package io.duckmc.content;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class PackSelectionTransaction {
   private final PackSelectionTransaction.Backend backend;
   private final Executor executor;
   private boolean busy;

   public PackSelectionTransaction(PackSelectionTransaction.Backend backend, Executor executor) {
      this.backend = backend;
      this.executor = executor;
   }

   public CompletableFuture<ResourcePackService.Outcome> change(String id, boolean enabled) {
      CompletableFuture<ResourcePackService.Outcome> result = new CompletableFuture<>();
      this.executor.execute(() -> {
         if (this.busy) {
            result.complete(new ResourcePackService.Outcome(false, "A resource reload is already running."));
         } else {
            this.busy = true;

            List<String> before;
            try {
               before = List.copyOf(this.backend.selected());
            } catch (RuntimeException error) {
               this.finish(result, false, "Could not read resource pack selection.");
               return;
            }

            ArrayList<String> requested = new ArrayList<>(before);
            requested.remove(id);
            if (enabled) {
               requested.add(id);
            }

            if (requested.equals(before)) {
               this.finish(result, true, "Pack selection unchanged.");
            } else {
               try {
                  this.backend.select(requested);
                  this.backend.reload().whenCompleteAsync((value, error) -> {
                     if (error == null) {
                        try {
                           if (this.backend.selected().contains(id) != enabled) {
                              this.finish(result, false, "Pack selection changed during reload; left unchanged.");
                              return;
                           }

                           this.backend.persist();
                           this.finish(result, true, enabled ? "Pack enabled." : "Pack disabled.");
                        } catch (RuntimeException failure) {
                           this.rollback(result, id, enabled, before, requested);
                        }
                     } else {
                        this.rollback(result, id, enabled, before, requested);
                     }
                  }, this.executor);
               } catch (RuntimeException error) {
                  this.rollback(result, id, enabled, before, requested);
               }
            }
         }
      });
      return result;
   }

   private void rollback(CompletableFuture<ResourcePackService.Outcome> result, String id, boolean enabled, List<String> before, List<String> requested) {
      try {
         List<String> current = this.backend.selected();
         List<String> restored;
         if (current.equals(requested)) {
            restored = before;
         } else {
            ArrayList<String> merged = new ArrayList<>(current);
            if (enabled && !before.contains(id)) {
               merged.remove(id);
            } else if (!enabled && before.contains(id) && !merged.contains(id)) {
               merged.add(Math.min(before.indexOf(id), merged.size()), id);
            }

            restored = merged;
         }

         this.backend.select(restored);
         this.backend
            .reload()
            .whenCompleteAsync(
               (value, errorx) -> {
                  try {
                     this.backend.persist();
                  } catch (RuntimeException var5) {
                  }

                  this.finish(
                     result,
                     false,
                     errorx == null
                        ? "Reload failed; previous pack selection restored."
                        : "Reload failed; selection restored, but recovery reload also failed."
                  );
               },
               this.executor
            );
      } catch (RuntimeException error) {
         this.finish(result, false, "Reload failed; recovery could not be completed.");
      }
   }

   private void finish(CompletableFuture<ResourcePackService.Outcome> result, boolean successful, String message) {
      this.busy = false;
      result.complete(new ResourcePackService.Outcome(successful, message));
   }

   public interface Backend {
      List<String> selected();

      void select(List<String> var1);

      CompletableFuture<Void> reload();

      void persist();
   }
}
