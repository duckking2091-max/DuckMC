package io.duckmc.pvp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PvpCards {
   private PvpCards() {
   }

   public static List<String> lines(String id, PvpSnapshot s) {
      if (!s.connected()) {
         return List.of();
      }

      return switch (id) {
         case "shield_status" -> List.of();
         case "target_info" -> (List)s.target().map(t -> List.of(decimal(t.distance()) + " blocks", decimal(t.health()) + " HP")).orElse(List.of());
         case "armor_status" -> {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("Armor");
            if (s.armor().isEmpty()) {
               lines.add("None equipped");
            }

            for (PvpSnapshot.Armor a : s.armor()) {
               lines.add(a.slot() + ": " + (a.maximum() == 0 ? "No durability" : a.remaining() + "/" + a.maximum()));
            }

            yield List.copyOf(lines);
         }
         case "totem_status" -> List.of("Totems", s.totems() + " in inventory");
         case "attack_indicator" -> List.of("Attack", Math.round(s.cooldownProgress() * 100.0) + "% ready");
         case "ping_display" -> List.of("Ping", s.pingMillis() < 0 ? "Unavailable" : s.pingMillis() + " ms");
         case "low_health_alert" -> s.health() <= s.lowHealthThreshold() ? List.of("Low health", decimal(s.health()) + " HP") : List.of();
         default -> throw new IllegalArgumentException("Unknown card: " + id);
      };
   }

   private static String decimal(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }
}
