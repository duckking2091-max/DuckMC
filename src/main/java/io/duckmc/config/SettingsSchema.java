package io.duckmc.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.duckmc.advantages.AdvantagePolicy;
import io.duckmc.pvp.PvpHudLayout;
import io.duckmc.pvp.PvpPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class SettingsSchema {
   public static final Set<String> ADVANTAGES = Set.of("stun_slam", "auto_crystal", "auto_breach", "informer", "auto_totem", "auto_shield_break", "auto_lunge");
   private static final Map<String, SettingsSchema.Definition> DEFINITIONS = create();

   private SettingsSchema() {
   }

   private static Map<String, SettingsSchema.Definition> create() {
      Map<String, SettingsSchema.Definition> d = new LinkedHashMap<>();
      text(d, "profile.description", "", 80);
      number(d, "profile.modified", 0.0, 0.0, 4.0E15);
      color(d, "ui.accent", 4294956103L);
      color(d, "ui.background", 4279375132L);
      color(d, "ui.gradientEnd", 4280559926L);
      color(d, "category.performance", 4284864920L);
      color(d, "category.pvp", 4294930552L);
      color(d, "category.content", 4285115647L);
      color(d, "category.advantages", 4294947655L);
      bool(d, "ui.gradient", true);
      bool(d, "ui.blur", true);
      bool(d, "ui.shadows", true);
      bool(d, "ui.branding", true);
      bool(d, "ui.reducedMotion", false);
      number(d, "ui.opacity", 0.9, 0.0, 1.0);
      number(d, "ui.radius", 10.0, 0.0, 24.0);
      number(d, "ui.animationSpeed", 1.0, 0.0, 3.0);
      number(d, "ui.scale", 1.0, 0.5, 2.0);
      number(d, "ui.fontScale", 1.0, 0.75, 2.0);
      text(d, "ui.name", "DuckMC", 40);
      text(d, "ui.font", "minecraft:default", 100);
      number(d, "hud.x", 0.02, 0.0, 1.0);
      number(d, "hud.y", 0.02, 0.0, 1.0);
      number(d, "hud.scale", 1.0, 0.5, 3.0);
      number(d, "hud.opacity", 0.9, 0.1, 1.0);
      color(d, "hud.color", 4294967295L);
      bool(d, "hud.visible", false);
      integer(d, "key.open", 61, -1, 348);
      integer(d, "key.open.modifiers", 0, 0, 15);
      String[] modules = new String[]{
         "fps_boost",
         "frame_pacing",
         "background_throttling",
         "loading_ui",
         "cache_management",
         "entity_culling",
         "distant_animation",
         "entity_distance",
         "item_limits",
         "gui_cache",
         "diagnostics",
         "remove_particles",
         "mace_lines",
         "hitbox_colors",
         "optimize_crystals",
         "shield_status",
         "target_info",
         "armor_status",
         "totem_status",
         "attack_indicator",
         "ping_display",
         "low_health_alert",
         "low_fire",
         "low_shield",
         "low_totems"
      };
      Set<String> all = new TreeSet<>(Arrays.asList(modules));
      all.addAll(ADVANTAGES);

      for (String id : all) {
         bool(d, "module." + id, false);
         integer(d, "key." + id, -1, -1, 348);
         integer(d, "key." + id + ".modifiers", 0, 0, 15);
      }

      bool(d, "module.anchor_assist", false);
      integer(d, "key.anchor_assist", -1, -1, 348);
      integer(d, "key.anchor_assist.modifiers", 0, 0, 15);
      integer(d, "performance.foregroundFps", 361, 30, 361);
      integer(d, "performance.backgroundFps", 30, 5, 60);
      number(d, "performance.entityDistance", 96.0, 16.0, 256.0);
      integer(d, "performance.itemLimit", 128, 16, 1024);
      integer(d, "performance.orbLimit", 64, 8, 512);
      integer(d, "performance.cacheMiB", 16, 1, 128);
      text(d, "performance.fpsBoostOwnership", "{}", 8192);
      int cardIndex = 0;

      for (String id : PvpPolicy.CARDS) {
         String prefix = "hud." + id + ".";
         number(d, prefix + "x", cardIndex < 4 ? 0.02 : 0.55, 0.0, 1.0);
         number(d, prefix + "y", 0.12 + cardIndex++ % 4 * 0.22, 0.0, 1.0);
         number(d, prefix + "scale", 0.75, 0.5, 3.0);
         number(d, prefix + "opacity", 0.9, 0.1, 1.0);
         color(d, prefix + "color", 4294967295L);
         number(d, prefix + "textScale", 1.0, 0.5, 3.0);
         boolean card = !PvpHudLayout.anchored(id) && !id.equals("shield_status");
         bool(d, prefix + "background", card);
         bool(d, prefix + "icon", true);
         bool(d, prefix + "free", false);
         number(d, prefix + "spacing", 1.0, 0.0, 8.0);
         bool(d, prefix + "durability", true);
         bool(d, prefix + "offhand", true);
      }

      number(d, "pvp.lowHealthThreshold", 6.0, 1.0, 20.0);
      color(d, "pvp.guideColor", 4294930552L);
      color(d, "pvp.playerOutlineColor", 4294930552L);
      color(d, "pvp.entityOutlineColor", 4294956103L);

      for (String particle : PvpPolicy.COMBAT_PARTICLES) {
         bool(d, "pvp.particle." + particle.substring(10), true);
      }

      integer(d, "advantages.stun_slam.axeSlot", 1, 1, 9);
      integer(d, "advantages.stun_slam.maceSlot", 2, 1, 9);
      integer(d, "advantages.stun_slam.windowTicks", 10, 2, 20);
      integer(d, "advantages.auto_crystal.crystalSlot", 3, 1, 9);
      number(d, "advantages.auto_crystal.range", 3.0, 1.0, 4.5);
      integer(d, "advantages.auto_breach.maceSlot", 2, 1, 9);
      number(d, "advantages.informer.range", 16.0, 1.0, 32.0);
      number(d, "advantages.informer.volume", 0.7, 0.0, 1.0);
      number(d, "advantages.informer.windPitch", 1.2, 0.5, 2.0);
      number(d, "advantages.informer.pearlPitch", 0.8, 0.5, 2.0);
      number(d, "advantages.auto_totem.health", 6.0, 1.0, 20.0);
      bool(d, "advantages.auto_totem.includeAbsorption", true);
      integer(d, "advantages.auto_totem.inventorySlot", 0, 0, 36);
      integer(d, "advantages.auto_totem.stepTicks", 1, 1, 4);
      bool(d, "advantages.auto_totem.openInventory", false);
      integer(d, "advantages.auto_shield_break.axeSlot", 1, 1, 9);
      integer(d, "advantages.anchor_assist.anchorSlot", 4, 1, 9);
      integer(d, "advantages.anchor_assist.glowstoneSlot", 5, 1, 9);
      integer(d, "advantages.anchor_assist.stepTicks", 2, 1, 4);

      for (String id : AdvantagePolicy.TIMED_MODULES) {
         if (!id.equals("anchor_assist")) {
            integer(d, "advantages." + id + ".stepTicks", 1, 1, 4);
         }
      }

      integer(d, "advantages.auto_lunge.spearSlot", 6, 1, 9);
      integer(d, "advantages.auto_lunge.windSlot", 7, 1, 9);
      return Collections.unmodifiableMap(d);
   }

   private static void bool(Map<String, SettingsSchema.Definition> d, String k, boolean v) {
      d.put(k, new SettingsSchema.Definition(k, SettingsSchema.Kind.BOOLEAN, new JsonPrimitive(v), 0.0, 1.0));
   }

   private static void number(Map<String, SettingsSchema.Definition> d, String k, double v, double min, double max) {
      d.put(k, new SettingsSchema.Definition(k, SettingsSchema.Kind.NUMBER, new JsonPrimitive(v), min, max));
   }

   private static void integer(Map<String, SettingsSchema.Definition> d, String k, int v, int min, int max) {
      d.put(k, new SettingsSchema.Definition(k, SettingsSchema.Kind.INTEGER, new JsonPrimitive(v), min, max));
   }

   private static void color(Map<String, SettingsSchema.Definition> d, String k, long v) {
      d.put(k, new SettingsSchema.Definition(k, SettingsSchema.Kind.COLOR, new JsonPrimitive(v), 0.0, 4.294967295E9));
   }

   private static void text(Map<String, SettingsSchema.Definition> d, String k, String v, int max) {
      d.put(k, new SettingsSchema.Definition(k, SettingsSchema.Kind.TEXT, new JsonPrimitive(v), 0.0, max));
   }

   public static Map<String, SettingsSchema.Definition> definitions() {
      return DEFINITIONS;
   }

   public static JsonObject defaults() {
      JsonObject o = new JsonObject();
      DEFINITIONS.forEach((k, d) -> o.add(k, d.initial().deepCopy()));
      return o;
   }

   public static JsonObject validate(JsonObject input) {
      JsonObject result = defaults();
      input.entrySet().forEach(e -> {
         SettingsSchema.Definition d = DEFINITIONS.get(e.getKey());
         if (d == null) {
            throw new IllegalArgumentException("Unknown setting: " + (String)e.getKey());
         }

         result.add((String)e.getKey(), d.validate((JsonElement)e.getValue()));
      });
      result.addProperty("module.anchor_assist", false);
      result.addProperty("key.anchor_assist", -1);
      result.addProperty("key.anchor_assist.modifiers", 0);
      return result;
   }

   public record Definition(String key, SettingsSchema.Kind kind, JsonPrimitive initial, double min, double max) {
      public JsonElement validate(JsonElement value) {
         if (!this.key.equals("ui.font")
            || value != null
               && value.isJsonPrimitive()
               && value.getAsJsonPrimitive().isString()
               && value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9_-]+(?:[./][a-z0-9_-]+)*")) {
            if (value != null && value.isJsonPrimitive()) {
               JsonPrimitive p = value.getAsJsonPrimitive();

               boolean valid = switch (this.kind) {
                  case BOOLEAN -> p.isBoolean();
                  case NUMBER, INTEGER, COLOR -> p.isNumber()
                     && Double.isFinite(p.getAsDouble())
                     && p.getAsDouble() >= this.min
                     && p.getAsDouble() <= this.max
                     && (this.kind == SettingsSchema.Kind.NUMBER || p.getAsDouble() == Math.rint(p.getAsDouble()));
                  case TEXT -> p.isString() && p.getAsString().length() <= this.max && p.getAsString().chars().noneMatch(Character::isISOControl);
               };
               if (!valid) {
                  throw new IllegalArgumentException("Invalid " + this.key);
               } else if (this.key.startsWith("key.") && !this.key.endsWith(".modifiers") && p.getAsInt() != -1 && p.getAsInt() < 32) {
                  throw new IllegalArgumentException("Invalid keyboard key: " + this.key);
               } else {
                  return p.deepCopy();
               }
            } else {
               throw new IllegalArgumentException("Invalid " + this.key);
            }
         } else {
            throw new IllegalArgumentException("Font must be a Minecraft resource identifier");
         }
      }
   }

   public enum Kind {
      BOOLEAN,
      NUMBER,
      INTEGER,
      COLOR,
      TEXT;
   }
}
