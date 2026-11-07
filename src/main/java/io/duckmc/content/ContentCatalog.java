package io.duckmc.content;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ContentCatalog extends ContentProvider {
   CompletableFuture<ContentDetails> details(ContentResult var1, String var2, CancellationToken var3);

   CompletableFuture<List<String>> categories(ContentResult.Kind var1, CancellationToken var2);
}
