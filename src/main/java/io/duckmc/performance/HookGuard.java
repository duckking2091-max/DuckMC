package io.duckmc.performance;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class HookGuard {
   private HookGuard() {
   }

   public static <T> T run(Supplier<T> callback, T vanillaResult, Consumer<RuntimeException> failure) {
      try {
         return callback.get();
      } catch (RuntimeException error) {
         failure.accept(error);
         return vanillaResult;
      }
   }
}
