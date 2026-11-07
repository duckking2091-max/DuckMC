package io.duckmc.performance;

import com.google.gson.JsonObject;
import java.util.Objects;

public final class PerformancePolicy {
   private final boolean framePacing;
   private final boolean backgroundThrottling;
   private final boolean entityCulling;
   private final boolean distantAnimation;
   private final boolean entityDistance;
   private final boolean itemLimits;
   private final int foregroundFps;
   private final int backgroundFps;
   private final double maximumEntityDistance;
   private final int itemLimit;
   private final int orbLimit;
   private int renderedItems;
   private int renderedOrbs;

   private PerformancePolicy(JsonObject settings) {
      this.framePacing = bool(settings, "module.frame_pacing");
      this.backgroundThrottling = bool(settings, "module.background_throttling");
      this.entityCulling = bool(settings, "module.entity_culling");
      this.distantAnimation = bool(settings, "module.distant_animation");
      this.entityDistance = bool(settings, "module.entity_distance");
      this.itemLimits = bool(settings, "module.item_limits");
      this.foregroundFps = integer(settings, "performance.foregroundFps");
      this.backgroundFps = integer(settings, "performance.backgroundFps");
      this.maximumEntityDistance = number(settings, "performance.entityDistance");
      this.itemLimit = integer(settings, "performance.itemLimit");
      this.orbLimit = integer(settings, "performance.orbLimit");
   }

   public static PerformancePolicy from(JsonObject validatedSettings) {
      return new PerformancePolicy(Objects.requireNonNull(validatedSettings, "validatedSettings"));
   }

   public long frameBudgetNanos(boolean focused) {
      if (!focused && this.backgroundThrottling) {
         return 1000000000L / this.backgroundFps;
      } else {
         return this.framePacing && this.foregroundFps <= 360 ? 1000000000L / this.foregroundFps : 0L;
      }
   }

   public synchronized void resetFrame() {
      this.renderedItems = 0;
      this.renderedOrbs = 0;
   }

   public synchronized boolean shouldRender(PerformancePolicy.RenderCandidate candidate) {
      Objects.requireNonNull(candidate, "candidate");
      if (!candidate.targeted() && !candidate.important()) {
         if (affectsEntityRender(candidate.category())) {
            if (this.entityCulling && !candidate.visible()) {
               return false;
            }

            if (this.entityDistance && candidate.distance() > this.maximumEntityDistance) {
               return false;
            }
         }

         if (!this.itemLimits) {
            return true;
         }

         return switch (candidate.category()) {
            case ITEM -> this.renderedItems++ < this.itemLimit;
            case EXPERIENCE_ORB -> this.renderedOrbs++ < this.orbLimit;
            default -> true;
         };
      } else {
         return true;
      }
   }

   public int animationInterval(double distance) {
      if (!Double.isFinite(distance) || distance < 0.0) {
         throw new IllegalArgumentException("Distance must be finite and nonnegative");
      } else if (!this.distantAnimation || distance <= 32.0) {
         return 1;
      } else if (distance <= 64.0) {
         return 2;
      } else {
         return distance <= 128.0 ? 3 : 4;
      }
   }

   private static boolean affectsEntityRender(PerformancePolicy.RenderCategory category) {
      return category != PerformancePolicy.RenderCategory.OTHER;
   }

   private static boolean bool(JsonObject settings, String key) {
      try {
         return settings.get(key).getAsBoolean();
      } catch (RuntimeException exception) {
         throw new IllegalArgumentException("Expected validated boolean setting: " + key, exception);
      }
   }

   private static int integer(JsonObject settings, String key) {
      try {
         return settings.get(key).getAsInt();
      } catch (RuntimeException exception) {
         throw new IllegalArgumentException("Expected validated integer setting: " + key, exception);
      }
   }

   private static double number(JsonObject settings, String key) {
      try {
         return settings.get(key).getAsDouble();
      } catch (RuntimeException exception) {
         throw new IllegalArgumentException("Expected validated numeric setting: " + key, exception);
      }
   }

   public record RenderCandidate(PerformancePolicy.RenderCategory category, double distance, boolean visible, boolean targeted, boolean important) {
      public RenderCandidate(PerformancePolicy.RenderCategory category, double distance, boolean visible, boolean targeted, boolean important) {
         Objects.requireNonNull(category, "category");
         if (Double.isFinite(distance) && !(distance < 0.0)) {
            this.category = category;
            this.distance = distance;
            this.visible = visible;
            this.targeted = targeted;
            this.important = important;
         } else {
            throw new IllegalArgumentException("Distance must be finite and nonnegative");
         }
      }
   }

   public enum RenderCategory {
      ENTITY,
      ITEM,
      EXPERIENCE_ORB,
      OTHER;
   }
}
