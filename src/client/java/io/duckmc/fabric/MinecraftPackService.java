package io.duckmc.fabric;

import io.duckmc.content.ManagedPackStore;
import io.duckmc.content.PackSelectionTransaction;
import io.duckmc.content.ResourcePackService;
import io.duckmc.content.ManagedPackStore.Entry;
import io.duckmc.content.ManagedPackStore.PreparedDeletion;
import io.duckmc.content.PackSelectionTransaction.Backend;
import io.duckmc.content.ResourcePackService.InstalledPack;
import io.duckmc.content.ResourcePackService.Outcome;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

@Environment(EnvType.CLIENT)
public final class MinecraftPackService implements ResourcePackService, AutoCloseable {
   private final Minecraft client;
   private final ManagedPackStore store;
   private final PackSelectionTransaction selection;
   private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "DuckMC-pack-files");
      thread.setDaemon(true);
      return thread;
   });

   public MinecraftPackService(Minecraft client, ManagedPackStore store) {
      this(client, store, client::reloadResourcePacks);
   }

   public MinecraftPackService(Minecraft client, ManagedPackStore store, Supplier<CompletableFuture<Void>> reload) {
      this.client = client;
      this.store = store;
      this.selection = new PackSelectionTransaction(new Backend() {
         public List<String> selected() {
            return List.copyOf(client.getResourcePackRepository().getSelectedIds());
         }

         public void select(List<String> ids) {
            client.getResourcePackRepository().setSelected(ids);
         }

         public CompletableFuture<Void> reload() {
            return reload.get();
         }

         public void persist() {
            client.options.resourcePacks.clear();
            client.options.incompatibleResourcePacks.clear();

            for (Pack profile : client.getResourcePackRepository().getSelectedPacks()) {
               if (!profile.isFixedPosition()) {
                  client.options.resourcePacks.add(profile.getId());
                  if (!profile.getCompatibility().isCompatible()) {
                     client.options.incompatibleResourcePacks.add(profile.getId());
                  }
               }
            }

            client.options.save();
         }
      }, client::execute);
   }

   public List<InstalledPack> installed() {
      try {
         return this.store
            .entries()
            .stream()
            .map(entry -> new InstalledPack(entry.id(), entry.name(), this.client.getResourcePackRepository().getSelectedIds().contains(entry.packId())))
            .toList();
      } catch (IOException error) {
         throw new IllegalStateException("Could not read managed packs");
      }
   }

   public CompletableFuture<Outcome> enable(String id) {
      return this.change(id, true);
   }

   public CompletableFuture<Outcome> disable(String id) {
      return this.change(id, false);
   }

   private CompletableFuture<Outcome> change(String id, boolean enabled) {
      CompletableFuture<Outcome> outcome = new CompletableFuture<>();

      try {
         this.worker
            .execute(
               () -> {
                  try {
                     this.store.verify(id);
                     Entry entry = this.store.entry(id);
                     this.client
                        .execute(
                           () -> {
                              try {
                                 PackRepository manager = this.client.getResourcePackRepository();
                                 manager.reload();
                                 if (enabled && !manager.isAvailable(entry.packId())) {
                                    outcome.complete(new Outcome(false, "Minecraft could not load this pack."));
                                    return;
                                 }

                                 this.selection
                                    .change(entry.packId(), enabled)
                                    .whenComplete(
                                       (result, errorxx) -> outcome.complete(errorxx == null ? result : new Outcome(false, "Resource pack change failed."))
                                    );
                              } catch (RuntimeException errorx) {
                                 outcome.complete(new Outcome(false, "Resource pack change failed."));
                              }
                           }
                        );
                  } catch (Exception error) {
                     outcome.complete(new Outcome(false, "Managed pack changed, is missing, or is unavailable; left untouched."));
                  }
               }
            );
      } catch (RejectedExecutionException error) {
         outcome.complete(new Outcome(false, "Resource pack service is closed."));
      }

      return outcome;
   }

   public CompletableFuture<Outcome> delete(String id) {
      return this.disable(id).thenCompose(outcome -> {
         if (!outcome.successful()) {
            return CompletableFuture.completedFuture(outcome);
         }

         CompletableFuture<Outcome> result = new CompletableFuture<>();
         this.worker.execute(() -> {
            try {
               PreparedDeletion deletion = this.store.prepareDelete(id);
               this.client.execute(() -> {
                  try {
                     if (this.client.getResourcePackRepository().getSelectedIds().contains(deletion.packId())) {
                        result.complete(new Outcome(false, "Pack was re-enabled; deletion cancelled."));
                        return;
                     }

                     deletion.commit();
                     this.client.getResourcePackRepository().reload();
                     result.complete(new Outcome(true, "Moved to resourcepacks/.duckmc-trash; recoverable."));
                  } catch (Exception errorx) {
                     result.complete(new Outcome(false, "Pack could not be removed; file left untouched."));
                  }
               });
            } catch (Exception error) {
               result.complete(new Outcome(false, "Pack could not be removed; file left untouched."));
            }
         });
         return result;
      });
   }

   @Override
   public void close() {
      this.worker.shutdownNow();
   }
}
