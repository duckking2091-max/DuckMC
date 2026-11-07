package io.duckmc.pvp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PvpSnapshot(
   boolean connected,
   double health,
   double maxHealth,
   double absorption,
   double cooldownProgress,
   int pingMillis,
   boolean shieldBlocking,
   double shieldCooldownProgress,
   List<PvpSnapshot.Armor> armor,
   int totems,
   Optional<PvpSnapshot.Target> target,
   double lowHealthThreshold
) {
   public PvpSnapshot {
      finite(health, maxHealth, absorption, cooldownProgress, shieldCooldownProgress, lowHealthThreshold);
      if (!(health < 0.0)
         && !(maxHealth <= 0.0)
         && !(absorption < 0.0)
         && !(cooldownProgress < 0.0)
         && !(cooldownProgress > 1.0)
         && !(shieldCooldownProgress < 0.0)
         && !(shieldCooldownProgress > 1.0)
         && totems >= 0
         && !(lowHealthThreshold < 1.0)
         && !(lowHealthThreshold > 20.0)) {
         armor = List.copyOf(armor);
         if (armor.size() > 6) {
            throw new IllegalArgumentException("Too many equipment slots");
         }

         Objects.requireNonNull(target);
      } else {
         throw new IllegalArgumentException("Invalid status");
      }
   }

   private static String label(String text) {
      Objects.requireNonNull(text);
      return text.replaceAll("[\\p{Cntrl}§]", "").substring(0, Math.min(40, text.replaceAll("[\\p{Cntrl}§]", "").length()));
   }

   private static void finite(double... values) {
      for (double value : values) {
         if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite status");
         }
      }
   }

   public static PvpSnapshot empty() {
      return new PvpSnapshot(false, 0.0, 20.0, 0.0, 0.0, -1, false, 0.0, List.of(), 0, Optional.empty(), 6.0);
   }

   public record Armor(String slot, int remaining, int maximum, String item) {
      public Armor(String slot, int remaining, int maximum) {
         this(slot, remaining, maximum, "minecraft:air");
      }

      public Armor(String slot, int remaining, int maximum, String item) {
         slot = PvpSnapshot.label(slot);
         if (remaining >= 0 && maximum >= 0 && remaining <= maximum) {
            this.slot = slot;
            this.remaining = remaining;
            this.maximum = maximum;
            this.item = item;
         } else {
            throw new IllegalArgumentException("Invalid durability");
         }
      }
   }

   public record Target(String name, double health, double maxHealth, double distance) {
      public Target(String name, double health, double maxHealth, double distance) {
         name = PvpSnapshot.label(name);
         PvpSnapshot.finite(health, maxHealth, distance);
         if (!(health < 0.0) && !(maxHealth <= 0.0) && !(distance < 0.0)) {
            this.name = name;
            this.health = health;
            this.maxHealth = maxHealth;
            this.distance = distance;
         } else {
            throw new IllegalArgumentException("Invalid target");
         }
      }
   }
}
