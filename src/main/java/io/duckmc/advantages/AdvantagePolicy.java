package io.duckmc.advantages;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AdvantagePolicy {
   public static final Set<String> TIMED_MODULES = Set.of("stun_slam", "auto_breach", "auto_shield_break", "auto_lunge");
   public static final List<String> MODULES = List.of("stun_slam", "auto_crystal", "auto_breach", "informer", "auto_totem", "auto_shield_break", "auto_lunge");

   private AdvantagePolicy() {
   }

   public static boolean lungePitchAllowed(float pitch) {
      return pitch >= -34.0F && pitch <= 79.0F;
   }

   public static boolean lungeEligible(
      boolean freshRightClick, boolean spearWithLunge, boolean windReady, boolean attackReady, boolean hungerReady, boolean inWater, boolean gliding
   ) {
      return freshRightClick && spearWithLunge && windReady && attackReady && hungerReady && !inWater && !gliding;
   }

   public static boolean crystalEligible(
      boolean userUse, boolean obsidian, boolean emptySpace, boolean visible, double distance, double allowedReach, boolean crystalAvailable
   ) {
      return userUse
         && obsidian
         && emptySpace
         && visible
         && crystalAvailable
         && Double.isFinite(distance)
         && Double.isFinite(allowedReach)
         && distance >= 0.0
         && allowedReach > 0.0
         && distance <= allowedReach;
   }

   public static boolean totemNeeded(
      double health, double absorption, double threshold, boolean includeAbsorption, boolean offhandTotem, boolean available, boolean inventorySafe
   ) {
      return inventorySafe
         && available
         && !offhandTotem
         && Double.isFinite(health)
         && Double.isFinite(absorption)
         && Double.isFinite(threshold)
         && health >= 0.0
         && absorption >= 0.0
         && threshold >= 1.0
         && threshold <= 20.0
         && health + (includeAbsorption ? absorption : 0.0) <= threshold;
   }

   public static Optional<String> attackChoice(AdvantagePolicy.AttackInput input, Set<String> enabled) {
      if (!input.userAttack()) {
         return Optional.empty();
      } else if (enabled.contains("auto_shield_break") && input.visibleBlockingTarget() && input.axeValid()) {
         return Optional.of("auto_shield_break");
      } else if (!input.swordHeld()) {
         return Optional.empty();
      } else if (enabled.contains("auto_breach") && input.breachMaceValid()) {
         return Optional.of("auto_breach");
      } else {
         return enabled.contains("stun_slam") && input.axeValid() && input.maceValid() ? Optional.of("stun_slam") : Optional.empty();
      }
   }

   public record AttackInput(boolean userAttack, boolean visibleBlockingTarget, boolean swordHeld, boolean axeValid, boolean maceValid, boolean breachMaceValid) {
   }
}
