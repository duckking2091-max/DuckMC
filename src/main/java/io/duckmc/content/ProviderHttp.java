package io.duckmc.content;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow.Subscription;

public final class ProviderHttp {
   private final ProviderHttp.Exchange exchange;

   public ProviderHttp(ProviderHttp.Exchange exchange) {
      this.exchange = Objects.requireNonNull(exchange);
   }

   public ProviderHttp() {
      this(productionExchange());
   }

   public CompletableFuture<String> get(URI uri, Map<String, String> headers, CancellationToken token) {
      return this.get(uri, Map.copyOf(headers), token, 0);
   }

   private CompletableFuture<String> get(URI uri, Map<String, String> headers, CancellationToken token, int redirects) {
      if (token.isCancelled()) {
         return CompletableFuture.failedFuture(new CancellationException());
      }

      try {
         ContentResult.checkUri(uri);
         return token.track(this.exchange.send(uri, headers, token))
            .handle((response, error) -> {
               token.throwIfCancelled();
               if (error != null) {
                  if (error instanceof CompletionException && error.getCause() != null) {
                     error = error.getCause();
                  }

                  if (error instanceof ProviderException known) {
                     throw known;
                  } else {
                     throw new ProviderException(ProviderException.Kind.OFFLINE);
                  }
               } else {
                  return (ProviderHttp.Response)response;
               }
            })
            .thenCompose(
               response -> {
                  int status = response.status();
                  if (!Set.of(301, 302, 303, 307, 308).contains(status)) {
                     if (status == 429) {
                        throw new ProviderException(ProviderException.Kind.RATE_LIMITED);
                     } else if (status == 401 || status == 403) {
                        throw new ProviderException(ProviderException.Kind.AUTH_REQUIRED);
                     } else if (status != 200) {
                        throw new ProviderException(ProviderException.Kind.UNAVAILABLE);
                     } else if (response.body().getBytes(StandardCharsets.UTF_8).length > 2097152) {
                        throw new ProviderException(ProviderException.Kind.INVALID_RESPONSE);
                     } else {
                        return CompletableFuture.completedFuture(response.body());
                     }
                  } else {
                     if (redirects >= 3) {
                        throw new ProviderException(ProviderException.Kind.UNAVAILABLE);
                     }

                     String location = response.headers()
                        .entrySet()
                        .stream()
                        .filter(e -> e.getKey().equalsIgnoreCase("location"))
                        .map(Entry::getValue)
                        .findFirst()
                        .orElseThrow(() -> new ProviderException(ProviderException.Kind.INVALID_RESPONSE));

                     URI next;
                     try {
                        next = uri.resolve(location);
                        ContentResult.checkUri(next);
                     } catch (RuntimeException error) {
                        throw new ProviderException(ProviderException.Kind.INVALID_RESPONSE);
                     }

                     if (headers.isEmpty() || uri.getHost().equalsIgnoreCase(next.getHost()) && uri.getPort() == next.getPort()) {
                        return this.get(next, headers, token, redirects + 1);
                     } else {
                        throw new ProviderException(ProviderException.Kind.UNAVAILABLE);
                     }
                  }
               }
            );
      } catch (RuntimeException error) {
         return CompletableFuture.failedFuture(
            !(error instanceof CancellationException) && !(error instanceof ProviderException)
               ? new ProviderException(ProviderException.Kind.INVALID_RESPONSE)
               : error
         );
      }
   }

   private static ProviderHttp.Exchange productionExchange() {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).followRedirects(Redirect.NEVER).build();
      return (uri, headers, token) -> {
         if (Set.of("api.modrinth.com", "api.curseforge.com").contains(uri.getHost()) && uri.getPort() == -1) {
            Builder request = HttpRequest.newBuilder(uri)
               .timeout(Duration.ofSeconds(15L))
               .header("User-Agent", "DuckMC/0.4.0 (Fabric client content browser)")
               .header("Accept", "application/json");
            headers.forEach(request::header);
            ProviderHttp.LimitedBody subscriber = new ProviderHttp.LimitedBody(token);
            CompletableFuture<HttpResponse<String>> future = token.track(client.sendAsync(request.GET().build(), ignored -> subscriber));
            return future.orTimeout(15L, TimeUnit.SECONDS).whenComplete((value, error) -> {
               if (error != null) {
                  subscriber.abort();
               }
            }).thenApply(response -> {
               HashMap<String, String> map = new HashMap<>();
               response.headers().map().forEach((key, values) -> {
                  if (!values.isEmpty()) {
                     map.put(key, values.getFirst());
                  }
               });
               return new ProviderHttp.Response(response.statusCode(), map, response.body());
            });
         } else {
            return CompletableFuture.failedFuture(new ProviderException(ProviderException.Kind.UNAVAILABLE));
         }
      };
   }

   @FunctionalInterface
   public interface Exchange {
      CompletableFuture<ProviderHttp.Response> send(URI var1, Map<String, String> var2, CancellationToken var3);
   }

   private static final class LimitedBody implements BodySubscriber<String> {
      private final CancellationToken token;
      private final CompletableFuture<String> body = new CompletableFuture<>();
      private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      private volatile Subscription subscription;

      LimitedBody(CancellationToken token) {
         this.token = token;
      }

      @Override
      public CompletionStage<String> getBody() {
         return this.body;
      }

      @Override
      public void onSubscribe(Subscription value) {
         this.subscription = value;
         if (this.body.isDone()) {
            value.cancel();
         } else {
            value.request(1L);
         }
      }

      public void onNext(List<ByteBuffer> chunks) {
         try {
            this.token.throwIfCancelled();

            for (ByteBuffer chunk : chunks) {
               if ((long)this.bytes.size() + chunk.remaining() > 2097152L) {
                  throw new ProviderException(ProviderException.Kind.INVALID_RESPONSE);
               }

               byte[] data = new byte[chunk.remaining()];
               chunk.get(data);
               this.bytes.writeBytes(data);
            }

            this.subscription.request(1L);
         } catch (RuntimeException error) {
            this.body.completeExceptionally(error);
            this.abort();
         }
      }

      @Override
      public void onError(Throwable error) {
         this.body.completeExceptionally(error);
      }

      @Override
      public void onComplete() {
         try {
            this.token.throwIfCancelled();
            this.body
               .complete(
                  StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(this.bytes.toByteArray())).toString()
               );
         } catch (Exception error) {
            this.body.completeExceptionally(new ProviderException(ProviderException.Kind.INVALID_RESPONSE));
         }
      }

      void abort() {
         this.body.completeExceptionally(new ProviderException(ProviderException.Kind.OFFLINE));
         if (this.subscription != null) {
            this.subscription.cancel();
         }
      }
   }

   public record Response(int status, Map<String, String> headers, String body) {
   }
}
