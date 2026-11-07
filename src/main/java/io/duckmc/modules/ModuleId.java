package io.duckmc.modules;

import java.util.Objects;
import java.util.regex.Pattern;

public record ModuleId(String value) {
   private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9/._-]*");

   public ModuleId {
      Objects.requireNonNull(value, "value");
      if (!VALID_ID.matcher(value).matches()) {
         throw new IllegalArgumentException("Invalid module ID: " + value);
      }
   }

   @Override
   public String toString() {
      return this.value;
   }
}
