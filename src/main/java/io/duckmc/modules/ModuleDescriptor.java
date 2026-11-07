package io.duckmc.modules;

import java.util.Objects;

public record ModuleDescriptor(ModuleId id, String category, String description, boolean defaultEnabled) {
   public ModuleDescriptor {
      Objects.requireNonNull(id, "id");
      category = requireText(category, "category");
      description = requireText(description, "description");
   }

   private static String requireText(String value, String name) {
      Objects.requireNonNull(value, name);
      String trimmed = value.trim();
      if (trimmed.isEmpty()) {
         throw new IllegalArgumentException(name + " must not be blank");
      } else {
         return trimmed;
      }
   }
}
