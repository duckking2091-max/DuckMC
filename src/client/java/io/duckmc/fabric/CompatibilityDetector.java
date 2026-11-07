package io.duckmc.fabric;

import io.duckmc.performance.CompatibilityPolicy;
import io.duckmc.performance.CompatibilityPolicy.Evaluation;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class CompatibilityDetector {
   private CompatibilityDetector() {
   }

   public static Set<String> loadedMods() {
      return FabricLoader.getInstance().getAllMods().stream().map(m -> m.getMetadata().getId()).collect(Collectors.toUnmodifiableSet());
   }

   public static Evaluation evaluate() {
      return CompatibilityPolicy.evaluate(loadedMods());
   }
}
