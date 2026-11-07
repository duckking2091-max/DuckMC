package io.duckmc.content;

public final class ProviderException extends RuntimeException {
   private final ProviderException.Kind kind;

   public ProviderException(ProviderException.Kind kind) {
      super(switch (kind) {
         case OFFLINE -> "Provider could not be reached. Check your connection and try again.";
         case RATE_LIMITED -> "Provider rate limit reached. Wait before searching again.";
         case AUTH_REQUIRED -> "Enter a valid CurseForge API key in this browser.";
         case UNSUPPORTED -> "This provider does not support the selected content or filter.";
         case INVALID_RESPONSE -> "Provider returned invalid or oversized metadata.";
         case UNAVAILABLE -> "This content is unavailable for download through this provider.";
      });
      this.kind = kind;
   }

   public ProviderException.Kind kind() {
      return this.kind;
   }

   public enum Kind {
      OFFLINE,
      RATE_LIMITED,
      AUTH_REQUIRED,
      UNSUPPORTED,
      INVALID_RESPONSE,
      UNAVAILABLE;
   }
}
