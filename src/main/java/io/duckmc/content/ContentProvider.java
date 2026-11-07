package io.duckmc.content;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ContentProvider {
   CompletableFuture<List<ContentResult>> search(SearchQuery var1, CancellationToken var2);
}
