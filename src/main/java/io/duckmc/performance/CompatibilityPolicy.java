package io.duckmc.performance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class CompatibilityPolicy {
   private static final CompatibilityPolicy.HookStatus AVAILABLE = new CompatibilityPolicy.HookStatus(true, "Available; no recognized overlap detected");

   private CompatibilityPolicy() {
   }

   public static CompatibilityPolicy.Evaluation evaluate(Set<String> loadedModIds) {
      Objects.requireNonNull(loadedModIds, "loadedModIds");
      Set<String> mods = new TreeSet<>();

      for (String modId : loadedModIds) {
         if (modId == null || !modId.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid mod id: " + modId);
         }

         mods.add(modId);
      }

      EnumMap<CompatibilityPolicy.Hook, CompatibilityPolicy.HookStatus> statuses = new EnumMap<>(CompatibilityPolicy.Hook.class);

      for (CompatibilityPolicy.Hook hook : CompatibilityPolicy.Hook.values()) {
         statuses.put(hook, AVAILABLE);
      }

      Set<String> renderers = new TreeSet<>();
      if (mods.contains("sodium")) {
         renderers.add("sodium");
      }

      if (mods.contains("iris")) {
         renderers.add("iris");
      }

      if (!renderers.isEmpty()) {
         String reason = "Disabled conservatively: " + String.join(", ", renderers) + " replaces overlapping entity rendering paths";
         unavailable(
            statuses,
            reason,
            CompatibilityPolicy.Hook.ENTITY_CULLING,
            CompatibilityPolicy.Hook.DISTANT_ANIMATION,
            CompatibilityPolicy.Hook.ENTITY_DISTANCE,
            CompatibilityPolicy.Hook.ITEM_LIMITS
         );
      } else if (mods.contains("entityculling")) {
         unavailable(statuses, "Disabled: entityculling already owns entity visibility culling", CompatibilityPolicy.Hook.ENTITY_CULLING);
      }

      if (mods.contains("dynamic_fps")) {
         unavailable(
            statuses,
            "Disabled: dynamic_fps already owns focus-aware frame throttling",
            CompatibilityPolicy.Hook.FRAME_PACING,
            CompatibilityPolicy.Hook.BACKGROUND_THROTTLING
         );
      }

      if (mods.contains("immediatelyfast")) {
         unavailable(
            statuses,
            "Disabled conservatively: immediatelyfast optimizes overlapping GUI and loading rendering paths",
            CompatibilityPolicy.Hook.GUI_CACHE,
            CompatibilityPolicy.Hook.LOADING_UI
         );
      }

      return new CompatibilityPolicy.Evaluation(statuses);
   }

   private static void unavailable(
      EnumMap<CompatibilityPolicy.Hook, CompatibilityPolicy.HookStatus> statuses, String reason, CompatibilityPolicy.Hook... affectedHooks
   ) {
      CompatibilityPolicy.HookStatus status = new CompatibilityPolicy.HookStatus(false, reason);

      for (CompatibilityPolicy.Hook hook : affectedHooks) {
         statuses.put(hook, status);
      }
   }

   public record Evaluation(Map<CompatibilityPolicy.Hook, CompatibilityPolicy.HookStatus> statuses) {
      public Evaluation {
         Objects.requireNonNull(statuses, "statuses");
         statuses = Collections.unmodifiableMap(new EnumMap<>(statuses));
      }

      public CompatibilityPolicy.HookStatus status(CompatibilityPolicy.Hook hook) {
         return this.statuses.get(Objects.requireNonNull(hook, "hook"));
      }
   }

   public enum Hook {
      FRAME_PACING,
      BACKGROUND_THROTTLING,
      ENTITY_CULLING,
      DISTANT_ANIMATION,
      ENTITY_DISTANCE,
      ITEM_LIMITS,
      OWNED_CACHE,
      GUI_CACHE,
      LOADING_UI,
      DIAGNOSTICS;
   }

   public record HookStatus(boolean available, String reason) {
      public HookStatus {
         Objects.requireNonNull(reason, "reason");
         if (reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank");
         }
      }
   }
}
