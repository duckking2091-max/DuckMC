package io.duckmc.pvp;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

public final class PvpPolicy {
   public static final List<String> MODULES = List.of(
      "remove_particles",
      "mace_lines",
      "hitbox_colors",
      "optimize_crystals",
      "shield_status",
      "target_info",
      "armor_status",
      "totem_status",
      "low_fire",
      "low_shield",
      "low_totems"
   );
   public static final List<String> LEGACY = List.of("attack_indicator", "ping_display", "low_health_alert");
   public static final List<String> CARDS = List.of(
      "shield_status", "target_info", "armor_status", "totem_status", "attack_indicator", "ping_display", "low_health_alert"
   );
   public static final List<String> VISIBLE_CARDS = List.of("shield_status", "target_info", "armor_status", "totem_status");
   public static final Set<String> COMBAT_PARTICLES = Set.of(
      "minecraft:crit", "minecraft:enchanted_hit", "minecraft:damage_indicator", "minecraft:sweep_attack"
   );

   private PvpPolicy() {
   }

   public static Optional<UUID> closestMaceGuide(PvpPolicy.MaceContext context, List<PvpPolicy.MaceCandidate> candidates) {
      return context.mainHandMace() && Double.isFinite(context.playerY())
         ? closestGuide(
            candidates.stream()
               .filter(c -> Double.isFinite(c.feetY()) && c.feetY() < context.playerY())
               .filter(c -> context.playerY() - c.feetY() > 1.5)
               .map(PvpPolicy.MaceCandidate::visibility)
               .toList(),
            400.0
         )
         : Optional.empty();
   }

   public static boolean targetReachable(double eyeToHitDistance, boolean vanillaEligible) {
      return vanillaEligible && Double.isFinite(eyeToHitDistance) && eyeToHitDistance >= 0.0 && eyeToHitDistance <= 3.0;
   }

   public static OptionalInt maceGuideColor(double distanceSquared) {
      return Double.isFinite(distanceSquared) && !(distanceSquared < 0.0) && !(distanceSquared > 400.0)
         ? OptionalInt.of(distanceSquared <= 9.0 ? -16711936 : (distanceSquared <= 100.0 ? -256 : -65536))
         : OptionalInt.empty();
   }

   public static Optional<UUID> closestGuide(List<PvpPolicy.Candidate> candidates) {
      return closestGuide(candidates, 9.0);
   }

   private static Optional<UUID> closestGuide(List<PvpPolicy.Candidate> candidates, double maxDistanceSquared) {
      return candidates.stream()
         .filter(c -> c.visible && c.alive && !c.spectator && !c.self && c.distanceSquared <= maxDistanceSquared)
         .min(Comparator.comparingDouble(PvpPolicy.Candidate::distanceSquared).thenComparing(PvpPolicy.Candidate::id))
         .map(PvpPolicy.Candidate::id);
   }

   public static boolean hideParticle(String particleId, Set<String> selected) {
      return COMBAT_PARTICLES.contains(particleId) && selected.contains(particleId);
   }

   public record Candidate(UUID id, double distanceSquared, boolean visible, boolean alive, boolean spectator, boolean self) {
      public Candidate(UUID id, double distanceSquared, boolean visible, boolean alive, boolean spectator, boolean self) {
         Objects.requireNonNull(id);
         if (Double.isFinite(distanceSquared) && !(distanceSquared < 0.0)) {
            this.id = id;
            this.distanceSquared = distanceSquared;
            this.visible = visible;
            this.alive = alive;
            this.spectator = spectator;
            this.self = self;
         } else {
            throw new IllegalArgumentException("Invalid distance");
         }
      }
   }

   public record MaceCandidate(PvpPolicy.Candidate visibility, double feetY) {
   }

   public record MaceContext(boolean mainHandMace, boolean grounded, double velocityY, double fallenBlocks, double playerY) {
   }
}
