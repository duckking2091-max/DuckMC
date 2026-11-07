package io.duckmc.performance;

import com.google.gson.JsonObject;
import io.duckmc.config.StrictJson;

public final class FpsBoostRequirement {
   private FpsBoostRequirement() {
   }

   public static JsonObject effective(JsonObject saved, boolean sodiumInstalled) {
      JsonObject result = saved.deepCopy();
      if (!sodiumInstalled && result.get("module.fps_boost").getAsBoolean()) {
         try {
            result = PerformancePreset.restore(StrictJson.object(result.get("performance.fpsBoostOwnership").getAsString())).disableFpsBoost(result);
         } catch (IllegalArgumentException invalid) {
            for (String key : PerformancePreset.fpsBoostSettings()) {
               result.addProperty(key, false);
            }
         }

         result.addProperty("module.fps_boost", false);
         return result;
      } else {
         return result;
      }
   }
}
