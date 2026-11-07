package io.duckmc.content;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class ContentBrowserModel implements AutoCloseable {
   private final Executor executor;
   private SearchQuery query = new SearchQuery("", ContentResult.Kind.RESOURCE_PACK, "1.21.11", List.of(), "downloads", 0);
   private ContentBrowserModel.State state = new ContentBrowserModel.State(
      ContentBrowserModel.Status.IDLE, List.of(), "Choose a source and press Search. No requests yet."
   );
   private CancellationToken token = new CancellationToken();
   private long generation;
   private String openedSource;
   private SearchQuery openedQuery;
   private boolean openedCredentials;

   public void open(String source, ContentProvider provider, SearchQuery query, boolean credentialsAvailable) {
      if (!Objects.equals(this.openedSource, source)
         || !Objects.equals(this.openedQuery, query)
         || this.openedCredentials != credentialsAvailable
         || this.state.status() == ContentBrowserModel.Status.IDLE) {
         this.openedSource = source;
         this.openedQuery = query;
         this.openedCredentials = credentialsAvailable;
         if (!credentialsAvailable) {
            this.close();
            this.query = query;
            this.state = new ContentBrowserModel.State(ContentBrowserModel.Status.FAILED, List.of(), "Add your CurseForge key to config/duckmc/curseforge.key.");
         } else {
            this.search(provider, query);
         }
      }
   }

   public void credentialsChanged() {
      this.close();
      this.openedSource = null;
      this.openedQuery = null;
      this.state = new ContentBrowserModel.State(ContentBrowserModel.Status.IDLE, List.of(), "Credentials updated.");
   }

   public ContentBrowserModel(Executor executor) {
      this.executor = executor;
   }

   public SearchQuery query() {
      return this.query;
   }

   public ContentBrowserModel.State state() {
      return this.state;
   }

   public void search(ContentProvider provider, SearchQuery query) {
      this.close();
      this.query = query;
      this.openedQuery = query;
      long request = ++this.generation;
      this.token = new CancellationToken();
      this.state = new ContentBrowserModel.State(ContentBrowserModel.Status.LOADING, List.of(), "Searching…");

      try {
         provider.search(query, this.token)
            .whenComplete(
               (results, error) -> this.executor
                  .execute(
                     () -> {
                        if (request == this.generation && !this.token.isCancelled()) {
                           if (error == null) {
                              this.state = new ContentBrowserModel.State(
                                 ContentBrowserModel.Status.READY,
                                 results,
                                 results.isEmpty() ? "No compatible results found." : results.size() + " results; select one for details."
                              );
                           } else {
                              this.state = new ContentBrowserModel.State(ContentBrowserModel.Status.FAILED, List.of(), message(error));
                           }
                        }
                     }
                  )
            );
      } catch (RuntimeException error) {
         this.state = new ContentBrowserModel.State(ContentBrowserModel.Status.FAILED, List.of(), "Search could not be started.");
      }
   }

   public void reset(ContentResult.Kind kind) {
      this.close();
      this.query = new SearchQuery("", kind, "1.21.11", List.of(), "downloads", 0);
      this.openedSource = null;
      this.openedQuery = null;
      this.state = new ContentBrowserModel.State(ContentBrowserModel.Status.IDLE, List.of(), "Press Search to request content.");
   }

   private static String message(Throwable error) {
      while (error instanceof CompletionException && error.getCause() != null) {
         error = error.getCause();
      }

      return error instanceof ProviderException ? error.getMessage() : "Search could not be completed.";
   }

   @Override
   public void close() {
      this.generation++;
      this.token.cancel();
      if (this.state.status() == ContentBrowserModel.Status.LOADING) {
         this.state = new ContentBrowserModel.State(ContentBrowserModel.Status.IDLE, List.of(), "Search cancelled.");
      }
   }

   public record State(ContentBrowserModel.Status status, List<ContentResult> results, String message) {
      public State {
         results = List.copyOf(results);
      }
   }

   public enum Status {
      IDLE,
      LOADING,
      READY,
      FAILED;
   }
}
