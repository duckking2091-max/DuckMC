package io.duckmc.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcRuntime;
import io.duckmc.modules.ModuleDescriptor;
import io.duckmc.modules.ModuleHook;
import io.duckmc.modules.ModuleId;
import io.duckmc.pvp.CrystalLookup;
import io.duckmc.pvp.PvpPolicy;
import io.duckmc.pvp.PvpSnapshot;
import io.duckmc.pvp.ShieldObservations;
import io.duckmc.pvp.TotemPops;
import io.duckmc.pvp.PvpPolicy.Candidate;
import io.duckmc.pvp.PvpPolicy.MaceCandidate;
import io.duckmc.pvp.PvpPolicy.MaceContext;
import io.duckmc.pvp.PvpSnapshot.Armor;
import io.duckmc.pvp.PvpSnapshot.Target;
import io.duckmc.pvp.ShieldObservations.SoundCandidate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;

@Environment(EnvType.CLIENT)
public final class PvpAdapter {
   private UUID indicatorTarget;
   private long indicatorSeen;
   private final DuckMcRuntime runtime;
   private final Minecraft client;
   private final Set<String> active = new HashSet<>();
   private final Map<String, String> failures = new HashMap<>();
   private PvpSnapshot snapshot = PvpSnapshot.empty();
   private Optional<UUID> guide = Optional.empty();
   private final CrystalLookup crystalLookup = new CrystalLookup();
   private String crystalFeedback = "";
   private long feedbackUntil;
   private ClientLevel observedWorld;
   private final ShieldObservations shields = new ShieldObservations();
   private final TotemPops pops = new TotemPops();
   private final Map<UUID, Player> shieldPlayers = new HashMap<>();

   public PvpAdapter(DuckMcRuntime runtime, Minecraft client) {
      this.runtime = runtime;
      this.client = client;
      ShieldTintBridge.verifyRenderHook();

      for (final String name : PvpPolicy.MODULES) {
         runtime.moduleRegistry().register(new ModuleDescriptor(id(name), "PvP", name, false), new ModuleHook() {
            public void enable() {
               PvpAdapter.this.active.add(name);
               if (name.equals("remove_particles") && client.particleEngine != null) {
                  client.particleEngine.clearParticles();
               }
            }

            public void disable() {
               PvpAdapter.this.active.remove(name);
               if (name.equals("mace_lines")) {
                  PvpAdapter.this.guide = Optional.empty();
               }

               if (name.equals("optimize_crystals")) {
                  PvpAdapter.this.crystalFeedback = "";
               }

               if (name.equals("shield_status")) {
                  PvpAdapter.this.clearShields();
               }

               if (name.equals("totem_status")) {
                  PvpAdapter.this.pops.clear();
               }
            }
         });
      }
   }

   public void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)ignored -> this.sync());
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hit) -> {
         if (world.isClientSide() && player == this.client.player && this.enabled("optimize_crystals")) {
            this.guard("optimize_crystals", () -> {
               BlockState block = world.getBlockState(hit.getBlockPos());
               if (block.is(Blocks.OBSIDIAN) || block.is(Blocks.BEDROCK)) {
                  this.refreshCrystalFeedback();
                  this.feedbackUntil = System.nanoTime() + 1000000000L;
               }
            });
         }

         return InteractionResult.PASS;
      });
   }

   private static ModuleId id(String name) {
      return new ModuleId("duckmc:" + name);
   }

   public boolean enabled(String name) {
      return this.active.contains(name);
   }

   public String unavailableReason(String name) {
      if (this.failures.containsKey(name)) {
         return this.failures.get(name);
      }

      if ((name.equals("mace_lines") || name.equals("hitbox_colors")) && !PvpWorldRenderer.registered()) {
         return "World rendering callbacks are unavailable.";
      }

      if (name.equals("remove_particles") && !HookAvailability.has("duckmc$particle")) {
         return "Particle hook has not been verified in this client.";
      }

      if (name.equals("shield_status")) {
         for (String hook : List.of(
            "duckmc$shieldSound",
            "duckmc$shieldEntitySound",
            "duckmc$shieldClear",
            "duckmc$shieldRender",
            "duckmc$shieldCapture",
            "duckmc$shieldColor",
            "duckmc$shieldPattern"
         )) {
            if (!HookAvailability.has(hook)) {
               return "Shield rendering support is unavailable (" + hook.substring(7) + ").";
            }
         }
      }

      return "";
   }

   public void failed(String name, RuntimeException error) {
      this.failures.put(name, "Disabled safely after " + error.getClass().getSimpleName() + "; restart to retry.");
      this.runtime.moduleRegistry().setAvailability(id(name), false, this.failures.get(name));
      this.active.remove(name);
   }

   public void guard(String name, Runnable action) {
      if (this.enabled(name)) {
         try {
            action.run();
         } catch (RuntimeException error) {
            this.failed(name, error);
         }
      }
   }

   private <T> T observe(String name, Supplier<T> action, T fallback) {
      if (!this.enabled(name)) {
         return fallback;
      }

      try {
         return action.get();
      } catch (RuntimeException error) {
         this.failed(name, error);
         return fallback;
      }
   }

   private static double bounded(double value, double minimum, double maximum) {
      if (Double.isFinite(value) && !(value < minimum) && !(value > maximum)) {
         return value;
      } else {
         throw new IllegalArgumentException("Invalid observed status");
      }
   }

   public void setEnabled(String name, boolean value) {
      if (!PvpPolicy.MODULES.contains(name)) {
         throw new IllegalArgumentException("Unknown PvP module");
      }

      this.runtime.settings().preview("module." + name, new JsonPrimitive(value));
      this.sync();
   }

   public void sync() {
      JsonObject settings = this.runtime.config().activeSettings();

      for (String name : PvpPolicy.MODULES) {
         String reason = this.unavailableReason(name);
         this.runtime.moduleRegistry().setAvailability(id(name), reason.isEmpty(), reason);
         this.runtime.moduleRegistry().setEnabled(id(name), settings.get("module." + name).getAsBoolean());
      }

      LocalPlayer player = this.client.player;
      if (this.observedWorld != this.client.level) {
         this.observedWorld = this.client.level;
         this.crystalFeedback = "";
         this.feedbackUntil = 0L;
         this.snapshot = PvpSnapshot.empty();
         this.guide = Optional.empty();
         this.clearShields();
         this.pops.clear();
      }

      if (player != null && this.client.level != null && player.level() == this.client.level) {
         this.guard("shield_status", this::sampleShields);
         this.guide = this.currentMaceGuide();
         this.guard("totem_status", () -> {
            HashMap<UUID, Player> alive = new HashMap<>();

            for (AbstractClientPlayer other : this.client.level.players()) {
               if (other.isAlive()) {
                  alive.put(other.getUUID(), other);
               }
            }

            this.pops.retain(this.client.level, alive);
         });
         List<Armor> armor = this.observe(
            "armor_status",
            () -> {
               ArrayList<Armor> result = new ArrayList<>();

               for (EquipmentSlot slot : List.of(
                  EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
               )) {
                  ItemStack stack = player.getItemBySlot(slot);
                  if (!stack.isEmpty()) {
                     result.add(
                        new Armor(
                           slot.getName(),
                           Math.max(0, stack.getMaxDamage() - stack.getDamageValue()),
                           stack.getMaxDamage(),
                           BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                        )
                     );
                  }
               }

               return result;
            },
            List.of()
         );
         int totemCount = this.observe("totem_status", () -> {
            int totems = 0;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
               ItemStack stack = player.getInventory().getItem(i);
               if (stack.is(Items.TOTEM_OF_UNDYING)) {
                  totems += stack.getCount();
               }
            }

            return Math.max(0, totems);
         }, 0);
         int ping = this.observe("ping_display", () -> {
            PlayerInfo entry = this.client.getConnection() == null ? null : this.client.getConnection().getPlayerInfo(player.getUUID());
            return entry == null ? -1 : entry.getLatency();
         }, -1);
         Optional<Target> observedTarget = this.observe(
            "target_info",
            () -> {
               Optional<Target> target = Optional.empty();
               if (this.client.hitResult instanceof EntityHitResult hit
                  && hit.getEntity() instanceof LivingEntity entity
                  && entity.level() == this.client.level
                  && this.client.level.getEntity(entity.getId()) == entity
                  && entity.isAlive()
                  && !entity.isInvisibleTo(player)
                  && player.hasLineOfSight(entity)
                  && PvpPolicy.targetReachable(
                     player.getEyePosition().distanceTo(hit.getLocation()),
                     !player.isSpectator()
                        && entity.isPickable()
                        && player.isWithinEntityInteractionRange(entity, 0.0)
                        && player.getEyePosition().distanceTo(hit.getLocation()) <= player.entityInteractionRange()
                  )) {
                  target = Optional.of(
                     new Target(
                        entity.getDisplayName().getString(), entity.getHealth(), entity.getMaxHealth(), player.getEyePosition().distanceTo(hit.getLocation())
                     )
                  );
               }

               return target;
            },
            Optional.empty()
         );
         double[] shield = this.observe(
            "shield_status",
            () -> new double[]{player.isBlocking() ? 1.0 : 0.0, bounded(player.getCooldowns().getCooldownPercent(new ItemStack(Items.SHIELD), 0.0F), 0.0, 1.0)},
            new double[]{0.0, 0.0}
         );
         double[] health = this.observe(
            "low_health_alert",
            () -> new double[]{
               bounded(player.getHealth(), 0.0, Double.MAX_VALUE),
               bounded(player.getMaxHealth(), Double.MIN_VALUE, Double.MAX_VALUE),
               bounded(player.getAbsorptionAmount(), 0.0, Double.MAX_VALUE)
            },
            new double[]{0.0, 20.0, 0.0}
         );
         double cooldown = this.observe("attack_indicator", () -> bounded(player.getAttackStrengthScale(0.0F), 0.0, 1.0), 0.0);
         this.snapshot = new PvpSnapshot(
            true,
            health[0],
            health[1],
            health[2],
            cooldown,
            ping,
            shield[0] == 1.0,
            shield[1],
            armor,
            totemCount,
            observedTarget,
            settings.get("pvp.lowHealthThreshold").getAsDouble()
         );
      } else {
         this.snapshot = PvpSnapshot.empty();
         this.guide = Optional.empty();
         this.crystalFeedback = "";
         this.clearShields();
      }
   }

   private void clearShields() {
      this.shields.clear();
      this.shieldPlayers.clear();
   }

   private boolean visibleShieldPlayer(Player player) {
      return this.client.level != null
         && this.client.player != null
         && player.level() == this.client.level
         && this.client.level.getEntity(player.getId()) == player
         && player.isAlive()
         && !player.isSpectator()
         && !player.isInvisibleTo(this.client.player)
         && (player == this.client.player || this.client.player.hasLineOfSight(player))
         && (player.getMainHandItem().is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD));
   }

   private void sampleShields() {
      List<AbstractClientPlayer> present = this.client.level.players().stream().filter(this::visibleShieldPlayer).limit(128L).toList();
      HashSet<UUID> retained = new HashSet<>();

      for (AbstractClientPlayer player : present) {
         if (this.shieldPlayers.get(player.getUUID()) == player) {
            retained.add(player.getUUID());
         }
      }

      this.shields.retain(this.client.level, retained, this.client.level.getGameTime());
      this.shieldPlayers.clear();

      for (AbstractClientPlayer player : present) {
         this.shieldPlayers.put(player.getUUID(), player);
         if (player != this.client.player) {
            this.shields.sampleBlocking(this.client.level, player.getUUID(), player.isBlocking(), this.client.level.getGameTime());
         }
      }
   }

   private static boolean holdsShield(Player player) {
      return player.getMainHandItem().is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD);
   }

   public String shieldIndicator() {
      return this.observe("shield_status", () -> {
         if (this.client.level == null) {
            return "UNKNOWN";
         }

         long now = this.client.level.getGameTime();
         if (this.client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target) {
            this.indicatorTarget = target.getUUID();
            this.indicatorSeen = now;
         }

         if (this.observedWorld != this.client.level) {
            return "UNKNOWN";
         }

         if (this.indicatorTarget != null && now - this.indicatorSeen <= 100L) {
            Player tracked = this.shieldPlayers.get(this.indicatorTarget);
            if (tracked != null && tracked != this.client.player) {
               return this.shields.held(this.client.level, this.indicatorTarget, now).name();
            }
         }

         LocalPlayer self = this.client.player;
         if (self != null && holdsShield(self)) {
            ItemStack stack = self.getOffhandItem().is(Items.SHIELD) ? self.getOffhandItem() : self.getMainHandItem();
            return self.getCooldowns().isOnCooldown(stack) ? "DISABLED" : "READY";
         } else {
            return "UNKNOWN";
         }
      }, "UNKNOWN");
   }

   public int shieldTint(Player player) {
      ItemStack stack = player.getMainHandItem().is(Items.SHIELD) ? player.getMainHandItem() : player.getOffhandItem();
      return this.shieldTint(player, stack);
   }

   public int shieldTint(Player player, ItemStack stack) {
      return this.observe("shield_status", () -> {
         if (!stack.is(Items.SHIELD) || !this.visibleShieldPlayer(player)) {
            return -1;
         }

         if (player == this.client.player) {
            return player.getCooldowns().isOnCooldown(stack) ? -48060 : -11141291;
         }

         if (this.observedWorld == this.client.level && this.shieldPlayers.get(player.getUUID()) == player) {
            return switch (this.shields.held(this.client.level, player.getUUID(), this.client.level.getGameTime())) {
               case READY -> -11141291;
               case DISABLED -> -48060;
               case UNKNOWN -> -1;
               default -> throw new MatchException(null, null);
            };
         } else {
            return -1;
         }
      }, -1);
   }

   public void observeShieldSound(ClientboundSoundPacket packet) {
      this.guard(
         "shield_status",
         () -> {
            if (this.client.level != null && this.client.player != null && this.observedWorld == this.client.level) {
               ShieldObservations.correlate(
                     packet.getSound().equals(SoundEvents.SHIELD_BREAK),
                     this.client
                        .level
                        .players()
                        .stream()
                        .map(
                           player -> new SoundCandidate(
                              player.getUUID(), player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()), this.visibleShieldPlayer(player)
                           )
                        )
                        .toList()
                  )
                  .ifPresent(id -> this.shields.disabled(this.client.level, id, this.client.level.getGameTime()));
            }
         }
      );
   }

   public void observeShieldSound(ClientboundSoundEntityPacket packet) {
      this.guard(
         "shield_status",
         () -> {
            if (this.client.level != null
               && this.client.player != null
               && this.observedWorld == this.client.level
               && packet.getSound().equals(SoundEvents.SHIELD_BREAK)) {
               if (this.client.level.getEntity(packet.getId()) instanceof Player player && this.visibleShieldPlayer(player)) {
                  this.shields.disabled(this.client.level, player.getUUID(), this.client.level.getGameTime());
               }
            }
         }
      );
   }

   public boolean hideParticle(ParticleOptions effect) {
      return this.enabled("remove_particles");
   }

   public void observeTotemStatus(ClientboundEntityEventPacket packet) {
      this.guard("totem_status", () -> {
         if (this.client.level != null && this.client.level == this.observedWorld) {
            if (packet.getEntity(this.client.level) instanceof Player player && player.level() == this.client.level) {
               if (packet.getEventId() == 35) {
                  this.pops.pop(this.client.level, player.getUUID(), player);
               } else if (packet.getEventId() == 3) {
                  this.pops.forget(this.client.level, player.getUUID(), player);
               }
            }
         }
      });
   }

   public int observedPops(Player player) {
      return this.observe(
         "totem_status",
         () -> this.client.level != null && player.level() == this.client.level ? this.pops.count(this.client.level, player.getUUID(), player) : 0,
         0
      );
   }

   public Component totemLabel(Entity entity, Component original) {
      return this.observe(
         "totem_status",
         () -> {
            if (entity instanceof Player player
               && this.client.level != null
               && this.client.player != null
               && player.level() == this.client.level
               && player.isAlive()
               && !player.isInvisibleTo(this.client.player)) {
               int count = this.observedPops(player);
               return (Component)(count == 0 ? original : original.copy().append(Component.literal(" | -" + count).withStyle(ChatFormatting.GOLD)));
            } else {
               return original;
            }
         },
         original
      );
   }

   public PvpSnapshot snapshot() {
      return this.snapshot;
   }

   public Optional<UUID> guideTarget() {
      return this.guide;
   }

   public Optional<UUID> currentMaceGuide() {
      return this.observe(
         "mace_lines",
         () -> {
            LocalPlayer player = this.client.player;
            if (player != null && this.client.level != null && player.level() == this.client.level && player.isAlive() && !player.isSpectator()) {
               MaceContext context = new MaceContext(
                  player.getMainHandItem().is(Items.MACE), player.onGround(), player.getDeltaMovement().y, player.fallDistance, player.getY()
               );
               return PvpPolicy.closestMaceGuide(
                  context,
                  this.client
                     .level
                     .getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20.0), entity -> entity != player)
                     .stream()
                     .map(
                        other -> new MaceCandidate(
                           new Candidate(
                              other.getUUID(),
                              player.distanceToSqr(other),
                              !other.isInvisibleTo(player) && player.hasLineOfSight(other),
                              other.isAlive(),
                              other.isSpectator(),
                              other == player
                           ),
                           other.getY()
                        )
                     )
                     .toList()
               );
            } else {
               return Optional.empty();
            }
         },
         Optional.empty()
      );
   }

   private void refreshCrystalFeedback() {
      ArrayList<Boolean> slots = new ArrayList<>(9);

      for (int i = 0; i < 9; i++) {
         slots.add(this.client.player.getInventory().getItem(i).is(Items.END_CRYSTAL));
      }

      int slot = this.crystalLookup.slot(slots);
      this.crystalFeedback = slot < 0 ? "Crystal: none in hotbar" : "Crystal: hotbar " + (slot + 1) + " (manual use)";
   }

   public String crystalFeedback() {
      if (this.enabled("optimize_crystals")
         && System.nanoTime() < this.feedbackUntil
         && this.client.player != null
         && this.client.level == this.observedWorld
         && this.client.player.level() == this.client.level) {
         this.guard("optimize_crystals", this::refreshCrystalFeedback);
         return this.enabled("optimize_crystals") ? this.crystalFeedback : "";
      } else {
         return "";
      }
   }
}
