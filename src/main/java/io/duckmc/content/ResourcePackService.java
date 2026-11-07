package io.duckmc.content;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ResourcePackService {
   List<ResourcePackService.InstalledPack> installed();

   CompletableFuture<ResourcePackService.Outcome> enable(String var1);

   CompletableFuture<ResourcePackService.Outcome> disable(String var1);

   record InstalledPack(String id, String name, boolean enabled) {
   }

   record Outcome(boolean successful, String message) {
   }
}
