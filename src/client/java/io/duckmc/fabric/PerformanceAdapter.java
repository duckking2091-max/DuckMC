package io.duckmc.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcRuntime;
import io.duckmc.config.ConfigDocument;
import io.duckmc.config.SettingsSchema;
import io.duckmc.config.StrictJson;
import io.duckmc.modules.ModuleDescriptor;
import io.duckmc.modules.ModuleHook;
import io.duckmc.modules.ModuleId;
import io.duckmc.modules.ModuleState;
import io.duckmc.performance.CacheBudget;
import io.duckmc.performance.FpsBoostRequirement;
import io.duckmc.performance.FrameBenchmark;
import io.duckmc.performance.PerformanceMetrics;
import io.duckmc.performance.PerformancePolicy;
import io.duckmc.performance.PerformancePreset;
import io.duckmc.performance.RenderPhases;
import io.duckmc.performance.CompatibilityPolicy.Evaluation;
import io.duckmc.performance.CompatibilityPolicy.Hook;
import io.duckmc.performance.CompatibilityPolicy.HookStatus;
import io.duckmc.performance.FrameBenchmark.Report;
import io.duckmc.performance.PerformanceMetrics.Snapshot;
import io.duckmc.performance.PerformancePolicy.RenderCandidate;
import io.duckmc.performance.PerformancePolicy.RenderCategory;
import io.duckmc.performance.PerformancePreset.Activation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class PerformanceAdapter {
   public static final Map<String, Hook> HOOKS;
   private final DuckMcRuntime runtime;
   private final Minecraft client;
   private final Evaluation compatibility = CompatibilityDetector.evaluate();
   private final boolean sodiumInstalled = CompatibilityDetector.loadedMods().contains("sodium");
   private final Set<String> active = new HashSet<>();
   private final Map<String, String> failures = new HashMap<>();
   private final PerformanceMetrics metrics = new PerformanceMetrics();
   private final FrameBenchmark benchmark = new FrameBenchmark();
   private ConfigDocument applied;
   private PerformancePolicy policy;
   private CacheBudget<UUID, PerformanceAdapter.Animation> animations = new CacheBudget<>(1048576L, a -> 16384L);
   private CacheBudget<PerformanceAdapter.TextKey, List<FormattedCharSequence>> textCache = new CacheBudget<>(1048576L, lines -> 256L + lines.size() * 512L);
   private Object world;
   private long previousFrame;
   private int cachedMiB = -1;
   private long nextReadout;
   private String readout = "Collecting frame samples...";
   private String notice = "";

   public PerformanceAdapter(DuckMcRuntime runtime, Minecraft client) {
      this.runtime = runtime;
      this.client = client;

      for (final String name : HOOKS.keySet()) {
         runtime.moduleRegistry().register(new ModuleDescriptor(id(name), "Performance", name, false), new ModuleHook() {
            public void enable() {
               PerformanceAdapter.this.active.add(name);
            }

            public void disable() {
               PerformanceAdapter.this.active.remove(name);
               PerformanceAdapter.this.clearCaches();
            }
         });
      }

      this.sync();
   }

   private static ModuleId id(String name) {
      return new ModuleId("duckmc:" + name);
   }

   public boolean enabled(String name) {
      return !name.equals("fps_boost")
         ? this.active.contains(name)
         : this.sodiumInstalled && this.runtime.config().activeSettings().get("module.fps_boost").getAsBoolean();
   }

   public String unavailableReason(String name) {
      if (name.equals("fps_boost")) {
         return this.sodiumInstalled ? "" : "FPS Boost requires Sodium. Install Sodium for Minecraft 1.21.11, then restart Minecraft.";
      }

      if (this.failures.containsKey(name)) {
         return this.failures.get(name);
      }

      HookStatus status = this.compatibility.status(HOOKS.get(name));
      if (!status.available()) {
         return status.reason();
      }

      if (name.equals("item_limits") && !HookAvailability.has("duckmc$frame")) {
         return "Item limits require the verified per-frame reset hook.";
      }

      String injection = switch ((Hook)HOOKS.get(name)) {
         case FRAME_PACING, BACKGROUND_THROTTLING -> "duckmc$cap";
         case ENTITY_CULLING, ENTITY_DISTANCE, ITEM_LIMITS -> "duckmc$visibility";
         case DISTANT_ANIMATION -> "duckmc$animation";
         case DIAGNOSTICS -> "duckmc$frame";
         default -> "";
      };
      return !injection.isEmpty() && !HookAvailability.has(injection) ? "Required render hook has not been verified in this client." : "";
   }

   public void failed(String hook, RuntimeException error) {
      Collection<String> affected = switch (hook) {
         case "fps" -> List.of("frame_pacing", "background_throttling");
         case "visibility" -> List.of("entity_culling", "entity_distance", "item_limits");
         case "animation" -> List.of("distant_animation");
         default -> HOOKS.keySet();
      };
      this.notice = "Disabled safely after " + error.getClass().getSimpleName() + "; restart to retry.";

      for (String name : affected) {
         this.failures.put(name, this.notice);
         this.active.remove(name);
         this.runtime.moduleRegistry().setAvailability(id(name), false, this.notice);
      }

      this.applied = null;
      this.clearCaches();
      this.policy = PerformancePolicy.from(SettingsSchema.defaults());
   }

   public void sync() {
      boolean changed = this.applied != this.runtime.config();

      for (String name : HOOKS.keySet()) {
         String reason = this.unavailableReason(name);
         ModuleState before = this.runtime.moduleRegistry().state(id(name));
         this.runtime.moduleRegistry().setAvailability(id(name), reason.isEmpty(), reason);
         changed |= before != this.runtime.moduleRegistry().state(id(name));
      }

      if (changed) {
         this.applied = this.runtime.config();
         JsonObject settings = FpsBoostRequirement.effective(this.applied.activeSettings(), this.sodiumInstalled);

         for (String name : HOOKS.keySet()) {
            this.runtime.moduleRegistry().setEnabled(id(name), settings.get("module." + name).getAsBoolean());
            settings.addProperty("module." + name, this.enabled(name));
         }

         this.policy = PerformancePolicy.from(settings);
         int mib = this.enabled("cache_management") ? settings.get("performance.cacheMiB").getAsInt() : 2;
         if (mib != this.cachedMiB) {
            this.cachedMiB = mib;
            long half = mib * 1024L * 1024L / 2L;
            this.animations = new CacheBudget<>(half, a -> 16384L);
            this.textCache = new CacheBudget<>(half, lines -> 256L + lines.size() * 512L);
         }

         this.clearCaches();
      }
   }

   public void setEnabled(String name, boolean value) {
      if (name.equals("fps_boost")) {
         if (value && !this.sodiumInstalled) {
            this.notice = this.unavailableReason(name);
            return;
         }

         JsonObject current = this.runtime.config().activeSettings();
         if (current.get("module.fps_boost").getAsBoolean() == value) {
            return;
         }

         JsonObject result;
         if (value) {
            Activation activation = PerformancePreset.enableFpsBoost(current);
            result = activation.settings();
            result.addProperty("performance.fpsBoostOwnership", activation.preset().ownership().toString());
         } else {
            try {
               result = PerformancePreset.restore(StrictJson.object(current.get("performance.fpsBoostOwnership").getAsString())).disableFpsBoost(current);
            } catch (IllegalArgumentException invalid) {
               result = current;
               this.notice = "Preset ownership is invalid; existing manual settings were retained.";
            }

            result.addProperty("module.fps_boost", false);
            result.addProperty("performance.fpsBoostOwnership", "{}");
         }

         for (String key : PerformancePreset.fpsBoostSettings()) {
            this.runtime.settings().preview(key, result.get(key));
         }

         this.runtime.settings().preview("performance.fpsBoostOwnership", result.get("performance.fpsBoostOwnership"));
      } else {
         if (!HOOKS.containsKey(name)) {
            throw new IllegalArgumentException("Unknown performance module");
         }

         if (value && !this.unavailableReason(name).isEmpty()) {
            return;
         }

         this.runtime.settings().preview("module." + name, new JsonPrimitive(value));
      }

      this.sync();
   }

   private void recordBenchmark(long nanos) {
      Runtime memory = Runtime.getRuntime();
      Report report = this.benchmark.record(nanos, memory.totalMemory() - memory.freeMemory());
      if (report != null) {
         this.finishBenchmark(report);
      }
   }

   public String rendererName() {
      return this.sodiumInstalled ? "Sodium" : "Vanilla pipeline";
   }

   public boolean startBenchmark(int seconds) {
      if (this.benchmark.running()) {
         return false;
      }

      this.benchmark.start(seconds);
      RenderPhases.start();
      return true;
   }

   private void finishBenchmark(Report report) {
      Path file = this.client.gameDirectory.toPath().resolve("duckmc-benchmarks.csv");
      String saved = "Saved to duckmc-benchmarks.csv.";
      Path phaseFile = this.client.gameDirectory.toPath().resolve("duckmc-frame-phases.csv");
      String phases = RenderPhases.finish(report.frames());

      try {
         if (!Files.exists(phaseFile)) {
            Files.writeString(
               phaseFile,
               "renderer,avg_fps,world_ms,chunk_updates_ms,frustum_ms,entities_ms,block_layers_ms,game_tick_ms,game_render_ms,hud_ms,swap_ms,section_draw_ms\n"
            );
         }

         Files.writeString(phaseFile, this.rendererName() + String.format(Locale.ROOT, ",%.1f", report.averageFps()) + phases + "\n", StandardOpenOption.APPEND);
         if (!Files.exists(file)) {
            Files.writeString(file, "renderer," + Report.csvHeader() + "\n");
         }

         Files.writeString(file, this.rendererName() + "," + report.csv() + "\n", StandardOpenOption.APPEND);
      } catch (IOException failure) {
         saved = "The results file could not be written.";
      }

      if (this.client.player != null) {
         this.client
            .player
            .displayClientMessage(
               Component.literal(
                  String.format(
                     Locale.ROOT,
                     "[DuckMC] %s: %.1f FPS, 1%% low %.1f, %d stutters. %s",
                     this.rendererName(),
                     report.averageFps(),
                     report.onePercentLowFps(),
                     report.stutters(),
                     saved
                  )
               ),
               false
            );
      }
   }

   public void beginFrame() {
      this.sync();
      long now = System.nanoTime();
      if (this.previousFrame != 0L && now > this.previousFrame) {
         this.metrics.recordFrame(now - this.previousFrame);
         if (this.benchmark.running()) {
            this.recordBenchmark(now - this.previousFrame);
         }
      }

      this.previousFrame = now;
      this.policy.resetFrame();
      if (this.world != this.client.level) {
         this.world = this.client.level;
         this.clearCaches();
      }
   }

   public int limitFps(int vanilla, boolean focused) {
      this.sync();
      long nanos = this.policy.frameBudgetNanos(focused);
      return nanos == 0L ? vanilla : Math.min(vanilla, (int)Math.round(1.0E9 / nanos));
   }

   public boolean shouldRender(Entity entity, Frustum frustum, double x, double y, double z) {
      if (!this.enabled("entity_culling") && !this.enabled("entity_distance") && !this.enabled("item_limits")) {
         return true;
      }

      double distance = Math.sqrt(entity.distanceToSqr(x, y, z));
      RenderCategory category = entity instanceof ItemEntity
         ? RenderCategory.ITEM
         : (entity instanceof ExperienceOrb ? RenderCategory.EXPERIENCE_ORB : (entity instanceof LivingEntity ? RenderCategory.ENTITY : RenderCategory.OTHER));
      return this.policy
         .shouldRender(
            new RenderCandidate(
               category,
               distance,
               frustum.isVisible(entity.getBoundingBox().inflate(1.0)),
               entity == this.client.crosshairPickEntity,
               this.protectedEntity(entity)
            )
         );
   }

   private boolean protectedEntity(Entity entity) {
      return entity instanceof Player
         || !(entity instanceof Animal) && !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)
         || entity == this.client.crosshairPickEntity
         || entity.hasCustomName()
         || entity.isCurrentlyGlowing()
         || entity.isVehicle()
         || entity.isPassenger()
         || entity instanceof Leashable leashable && leashable.isLeashed()
         || entity instanceof LivingEntity living && (living.hurtTime > 0 || living.deathTime > 0);
   }

   public EntityRenderState animationState(EntityRenderer renderer, Entity entity, float tickProgress) {
      if (this.enabled("distant_animation") && entity instanceof Animal && !this.protectedEntity(entity) && this.client.player != null) {
         int interval = this.policy.animationInterval(Math.sqrt(entity.distanceToSqr(this.client.player)));
         if (interval <= 1) {
            return null;
         }

         PerformanceAdapter.Animation cached = (PerformanceAdapter.Animation)this.animations.get(entity.getUUID()).orElse(null);
         if (cached == null || cached.renderer() != renderer || entity.tickCount < cached.age() || entity.tickCount - cached.age() >= interval) {
            EntityRenderState state = renderer.createRenderState();
            renderer.extractRenderState(entity, state, tickProgress);
            cached = new PerformanceAdapter.Animation(renderer, entity.tickCount, state);
            this.animations.put(entity.getUUID(), cached);
         }

         EntityRenderState state = cached.state();
         Vec3 position = entity.getPosition(tickProgress);
         state.x = position.x;
         state.y = position.y;
         state.z = position.z;
         state.distanceToCameraSq = entity.distanceToSqr(this.client.player);
         return state;
      } else {
         return null;
      }
   }

   public List<FormattedCharSequence> wrap(Component text, int width) {
      if (!this.enabled("gui_cache")) {
         return this.client.font.split(text, width);
      }

      PerformanceAdapter.TextKey key = new PerformanceAdapter.TextKey(text.copy(), width);
      Optional<List<FormattedCharSequence>> cached = this.textCache.get(key);
      if (cached.isPresent()) {
         return cached.get();
      }

      List<FormattedCharSequence> result = List.copyOf(this.client.font.split(text, width));
      this.textCache.put(key, result);
      return result;
   }

   public void clearCaches() {
      this.animations.clear();
      this.textCache.clear();
   }

   public long cacheWeight() {
      return this.animations.currentWeight() + this.textCache.currentWeight();
   }

   public PerformanceMetrics metrics() {
      return this.metrics;
   }

   public String notice() {
      return this.notice;
   }

   public String readout() {
      long now = System.nanoTime();
      if (now >= this.nextReadout) {
         Snapshot sample = this.metrics.snapshot();
         Runtime memory = Runtime.getRuntime();
         this.readout = String.format(
            Locale.ROOT,
            "%.0f FPS | mean %.1f ms | p95 %.1f | p99 %.1f | heap %d/%d MiB",
            sample.framesPerSecond(),
            sample.meanMillis(),
            sample.p95Millis(),
            sample.p99Millis(),
            (memory.totalMemory() - memory.freeMemory()) / 1048576L,
            memory.maxMemory() / 1048576L
         );
         this.nextReadout = now + (this.enabled("loading_ui") ? 500000000L : 100000000L);
      }

      return this.readout;
   }

   static {
      LinkedHashMap<String, Hook> hooks = new LinkedHashMap<>();
      hooks.put("frame_pacing", Hook.FRAME_PACING);
      hooks.put("background_throttling", Hook.BACKGROUND_THROTTLING);
      hooks.put("loading_ui", Hook.LOADING_UI);
      hooks.put("cache_management", Hook.OWNED_CACHE);
      hooks.put("entity_culling", Hook.ENTITY_CULLING);
      hooks.put("distant_animation", Hook.DISTANT_ANIMATION);
      hooks.put("entity_distance", Hook.ENTITY_DISTANCE);
      hooks.put("item_limits", Hook.ITEM_LIMITS);
      hooks.put("gui_cache", Hook.GUI_CACHE);
      hooks.put("diagnostics", Hook.DIAGNOSTICS);
      HOOKS = Collections.unmodifiableMap(hooks);
   }

   @Environment(EnvType.CLIENT)
   private record Animation(Object renderer, int age, EntityRenderState state) {
   }

   @Environment(EnvType.CLIENT)
   private record TextKey(Component text, int width) {
   }
}
