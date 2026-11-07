package io.duckmc.content;

import java.util.List;
import java.util.Objects;

public record SearchQuery(String text, ContentResult.Kind kind, String minecraftVersion, List<String> categories, String sort, int offset) {
   public SearchQuery(String text, ContentResult.Kind kind, String minecraftVersion, List<String> categories, String sort, int offset) {
      Objects.requireNonNull(text);
      Objects.requireNonNull(kind);
      Objects.requireNonNull(minecraftVersion);
      Objects.requireNonNull(sort);
      categories = List.copyOf(categories);
      if (text.length() <= 200 && offset >= 0 && offset <= 10000) {
         this.text = text;
         this.kind = kind;
         this.minecraftVersion = minecraftVersion;
         this.categories = categories;
         this.sort = sort;
         this.offset = offset;
      } else {
         throw new IllegalArgumentException("Invalid search");
      }
   }
}
