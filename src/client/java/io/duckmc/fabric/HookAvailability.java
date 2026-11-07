package io.duckmc.fabric;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HookAvailability {
   private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();

   private HookAvailability() {
   }

   public static void applied(String hook) {
      APPLIED.add(hook);
   }

   public static boolean has(String hook) {
      return APPLIED.contains(hook);
   }
}
