package io.duckmc.ui.model;

import com.google.gson.JsonObject;
import io.duckmc.config.SettingsSchema;

public record Theme(
   long accent,
   long background,
   long gradientEnd,
   Theme.Categories categories,
   String name,
   double opacity,
   double radius,
   double scale,
   double fontScale,
   double animationSpeed,
   boolean gradient,
   boolean blur,
   boolean shadows,
   boolean branding,
   boolean reducedMotion,
   String font
) {
   public static Theme from(JsonObject source) {
      JsonObject p = SettingsSchema.validate(source);
      return new Theme(
         p.get("ui.accent").getAsLong(),
         p.get("ui.background").getAsLong(),
         p.get("ui.gradientEnd").getAsLong(),
         new Theme.Categories(
            p.get("category.performance").getAsLong(),
            p.get("category.pvp").getAsLong(),
            p.get("category.content").getAsLong(),
            p.get("category.advantages").getAsLong()
         ),
         p.get("ui.name").getAsString(),
         p.get("ui.opacity").getAsDouble(),
         p.get("ui.radius").getAsDouble(),
         p.get("ui.scale").getAsDouble(),
         p.get("ui.fontScale").getAsDouble(),
         p.get("ui.animationSpeed").getAsDouble(),
         p.get("ui.gradient").getAsBoolean(),
         p.get("ui.blur").getAsBoolean(),
         p.get("ui.shadows").getAsBoolean(),
         p.get("ui.branding").getAsBoolean(),
         p.get("ui.reducedMotion").getAsBoolean(),
         p.get("ui.font").getAsString()
      );
   }

   public record Categories(long performance, long pvp, long content, long advantages) {
   }
}
