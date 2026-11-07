package io.duckmc.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcRuntime;
import io.duckmc.advantages.AdvantagePolicy;
import io.duckmc.advantages.ClientTickClock;
import io.duckmc.advantages.HotbarResolver;
import io.duckmc.advantages.InputGuard;
import io.duckmc.advantages.OwnCrystalPlacement;
import io.duckmc.advantages.PopRefill;
import io.duckmc.advantages.SlotSequence;
import io.duckmc.advantages.ThrowObserver;
import io.duckmc.advantages.TimedSwap;
import io.duckmc.advantages.SlotSequence.Context;
import io.duckmc.advantages.TimedSwap.Action;
import io.duckmc.advantages.TimedSwap.Mode;
import io.duckmc.mixin.AdvantagesAttackInvoker;
import io.duckmc.modules.ModuleDescriptor;
import io.duckmc.modules.ModuleHook;
import io.duckmc.modules.ModuleId;
import io.duckmc.modules.ModuleState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

@Environment(EnvType.CLIENT)
public final class AdvantagesAdapter {
   private final DuckMcRuntime runtime;
   private final Minecraft client;
   private final AdvantagesActions actions;
   private final SlotSequence sequence = new SlotSequence();
   private final Map<String, String> failures = new HashMap<>();
   private final InputGuard input = new InputGuard();
   private final Map<Integer, ItemStack> ownedItems = new HashMap<>();
   private String pendingAttack = "";
   private int stunMaceSlot = -1;
   private UUID stunTarget;
   private long stunExpires;
   private final TimedSwap timed = new TimedSwap();
   private int secondarySlot;
   private int timedWindSlot;
   private Object timedWorld;
   private UUID timedTarget;
   private boolean timedLunge;
   private boolean assistedUseOwned;
   private final Map<String, String> notices = new HashMap<>();
   private Object totemWorld;
   private Object totemPlayer;
   private int totemPending;
   private final OwnCrystalPlacement ownCrystal = new OwnCrystalPlacement();
   private BlockPos crystalBase;
   private Context crystalContext;
   private ItemStack crystalBefore = ItemStack.EMPTY;
   private ItemStack crystalAfter = ItemStack.EMPTY;
   private int crystalSource;
   private float crystalYaw;
   private float crystalPitch;
   private final PopRefill refill = new PopRefill();
   private RefillInventoryScreen refillScreen;
   private ItemStack refillOffhand = ItemStack.EMPTY;
   private ItemStack prePopOffhand = ItemStack.EMPTY;
   private long refillDeadline;
   private long refillCloseAfter;
   private boolean refillConfirmed;
   private String totemProfile = "";
   private int totemSelection;
   private Object lastPopWorld;
   private Object lastPopPlayer;
   private long lastPopTick = -1L;
   private long ticks;
   private final ClientTickClock clock = new ClientTickClock();
   private final ThrowObserver throwsSeen = new ThrowObserver();
   private Object informerWorld;
   private boolean informerEnabled;

   public AdvantagesAdapter(DuckMcRuntime runtime, Minecraft client) {
      this.runtime = runtime;
      this.client = client;
      this.actions = new AdvantagesActions(client);

      for (final String id : AdvantagePolicy.MODULES) {
         runtime.moduleRegistry().register(new ModuleDescriptor(this.moduleId(id), "Advantages", id, false), new ModuleHook() {
            public void enable() {
            }

            public void disable() {
               if (id.equals("auto_totem")) {
                  AdvantagesAdapter.this.cancelRefill(true);
               }

               if (id.equals("auto_crystal")) {
                  AdvantagesAdapter.this.clearCrystal();
               }

               if (AdvantagesAdapter.this.sequence.activeModule().filter(id::equals).isPresent()) {
                  AdvantagesAdapter.this.abort();
               }
            }
         });
      }
   }

   public void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)ignored -> this.tick());
   }

   private ModuleId moduleId(String id) {
      return new ModuleId("duckmc:" + id);
   }

   private boolean livePlayer() {
      return this.client.player != null
         && this.client.level != null
         && this.client.gameMode != null
         && this.client.player.level() == this.client.level
         && this.client.player.isAlive()
         && !this.client.player.isSpectator();
   }

   private boolean usableInput() {
      return this.livePlayer()
         && this.client.screen == null
         && !this.runtime.settings().readOnly()
         && this.runtime.config().advantagesAcknowledged()
         && this.client.player.containerMenu == this.client.player.inventoryMenu
         && this.client.player.containerMenu.getCarried().isEmpty();
   }

   private Context context() {
      return new Context(
         this.client.level,
         this.client.player,
         this.runtime.config().activeProfile(),
         this.client.player == null ? -1 : this.client.player.getInventory().getSelectedSlot(),
         this.livePlayer()
      );
   }

   public String unavailableReason(String id) {
      if (this.failures.containsKey(id)) {
         return this.failures.get(id);
      }

      if (!this.runtime.config().advantagesAcknowledged()) {
         return "Read and accept the Advantages warning first.";
      }

      if (this.runtime.settings().readOnly()) {
         return "Configuration is read-only.";
      }

      boolean attackInput = Set.of("stun_slam", "auto_breach", "auto_shield_break", "auto_lunge", "auto_crystal").contains(id);
      boolean useInput = Set.of("auto_crystal", "auto_lunge").contains(id);
      if ((
            !attackInput
               || HookAvailability.has("duckmc$advantagesAttack")
                  && HookAvailability.has("duckmc$advantagesAfterAttack")
                  && this.client instanceof AdvantagesAttackInvoker
         )
         && (!useInput || HookAvailability.has("duckmc$advantagesUse") && HookAvailability.has("duckmc$advantagesFreshUse"))) {
         if (!id.equals("auto_crystal") || HookAvailability.has("duckmc$crystalUseStart") && HookAvailability.has("duckmc$crystalUseEnd")) {
            if (!id.equals("auto_totem")
               || HookAvailability.has("duckmc$totemPop") && HookAvailability.has("duckmc$totemInventory") && HookAvailability.has("duckmc$totemFullInventory")
               )
             {
               return AdvantagePolicy.MODULES.contains(id) ? "" : "Unknown feature.";
            } else {
               return "Inventory confirmation is unavailable.";
            }
         } else {
            return "Crystal placement observation is unavailable.";
         }
      } else {
         return "The input bridge is unavailable in this client.";
      }
   }

   public void setEnabled(String id, boolean enabled) {
      if (!AdvantagePolicy.MODULES.contains(id)) {
         throw new IllegalArgumentException("Unknown Advantage");
      }

      if (!this.runtime.settings().readOnly() && (!enabled || this.unavailableReason(id).isEmpty())) {
         this.runtime.settings().preview("module." + id, new JsonPrimitive(enabled));
         this.tick();
      }
   }

   public boolean active(String id) {
      return AdvantagePolicy.MODULES.contains(id)
         && this.usableInput()
         && this.unavailableReason(id).isEmpty()
         && this.runtime.config().activeSettings().get("module." + id).getAsBoolean()
         && this.runtime.moduleRegistry().state(this.moduleId(id)) == ModuleState.ENABLED;
   }

   public void tick() {
      boolean advanceTime = this.clock.observe(this.client.level, this.client.player, this.client.player == null ? 0 : this.client.player.tickCount);
      if (advanceTime) {
         this.ticks++;
      }

      if (this.totemWorld != this.client.level || this.totemPlayer != this.client.player) {
         this.cancelRefill(true);
      }

      if (this.totemPending > 0 && advanceTime && --this.totemPending == 0) {
         this.cancelRefill(true);
      }

      this.input.observeUseKey(this.client.options.keyUse.isDown());
      if (!this.client.options.keyUse.isDown()) {
         this.assistedUseOwned = false;
      }

      for (String id : AdvantagePolicy.MODULES) {
         String reason = this.unavailableReason(id);
         this.runtime.moduleRegistry().setAvailability(this.moduleId(id), reason.isEmpty(), reason);
         this.runtime
            .moduleRegistry()
            .setEnabled(
               this.moduleId(id), this.runtime.config().advantagesAcknowledged() && this.runtime.config().activeSettings().get("module." + id).getAsBoolean()
            );
      }

      try {
         this.advanceRefill();
      } catch (RuntimeException error) {
         this.failures.put("auto_totem", "Refill stopped safely. Restart to retry.");
         this.cancelRefill(true);
      }

      this.guarded("informer", this::observeThrows);
      if (!this.active("informer")) {
         this.throwsSeen.reset();
         this.informerEnabled = false;
         this.informerWorld = null;
      }

      if (!this.itemsValid()) {
         this.discard();
      } else {
         if (this.usableInput() && !this.sequence.activeModule().filter(idx -> !this.active(idx)).isPresent()) {
            this.actions.select(this.sequence.tick(this.context(), advanceTime));
            if (!this.sequence.busy()) {
               this.clearSequenceState();
            }
         } else {
            this.abort();
         }

         if (!this.active("auto_crystal")) {
            this.clearCrystal();
         }

         this.guarded(() -> {
            this.advanceTimed();
            this.advanceOwnCrystal();
         });
      }
   }

   private void abort() {
      if (!this.itemsValid()) {
         this.discard();
      } else {
         this.actions.select(this.sequence.abort(this.context()));
         this.clearSequenceState();
      }
   }

   private void clearSequenceState() {
      this.ownedItems.clear();
      this.pendingAttack = "";
      this.timed.clear();
      this.timedWorld = null;
      this.timedTarget = null;
      this.timedLunge = false;
   }

   private void discard() {
      Context c = this.context();
      this.sequence.abort(new Context(c.world(), c.player(), c.profile(), c.selectedSlot(), false));
      this.clearSequenceState();
   }

   private ItemStack normalized(ItemStack stack) {
      ItemStack copy = stack.copy();
      if (copy.isDamageableItem()) {
         copy.setDamageValue(0);
      }

      return copy;
   }

   private boolean itemsValid() {
      if (this.ownedItems.isEmpty()) {
         return true;
      } else {
         return !this.livePlayer() ? false : this.ownedItems.entrySet().stream().allMatch(e -> {
            ItemStack now = this.normalized(this.client.player.getInventory().getItem(e.getKey()));
            return now.getCount() == e.getValue().getCount() && ItemStack.isSameItemSameComponents(now, e.getValue());
         });
      }
   }

   private boolean begin(String id, int duration, int... slots) {
      if (this.active(id) && this.sequence.begin(id, this.context(), duration)) {
         this.ownedItems.put(this.context().selectedSlot(), this.normalized(this.client.player.getMainHandItem()));

         for (int slot : slots) {
            this.ownedItems.put(slot, this.normalized(this.client.player.getInventory().getItem(slot)));
         }

         return true;
      } else {
         return false;
      }
   }

   private void select(int slot) {
      this.actions.select(this.sequence.select(this.context(), slot));
   }

   private double number(String id, String key) {
      return this.runtime.config().activeSettings().get("advantages." + id + "." + key).getAsDouble();
   }

   private boolean flag(String id, String key) {
      return this.runtime.config().activeSettings().get("advantages." + id + "." + key).getAsBoolean();
   }

   private void refreshOwnedItems() {
      this.ownedItems.replaceAll((slot, old) -> this.normalized(this.stack(slot)));
   }

   public void totemPopped() {
      this.clearCrystal();
      if (this.active("auto_totem")) {
         long now = this.actionTick();
         if (this.lastPopWorld != this.client.level || this.lastPopPlayer != this.client.player || this.lastPopTick != now) {
            this.lastPopWorld = this.client.level;
            this.lastPopPlayer = this.client.player;
            this.lastPopTick = now;
            this.cancelRefill(true);
            this.abort();
            this.totemWorld = this.client.level;
            this.totemPlayer = this.client.player;
            this.totemProfile = this.runtime.config().activeProfile();
            this.totemSelection = this.context().selectedSlot();
            this.refillOffhand = this.client.player.getOffhandItem().copy();
            this.prePopOffhand = this.refillOffhand.copy();
            if (this.refillOffhand.is(Items.TOTEM_OF_UNDYING)) {
               this.refillOffhand.shrink(1);
            }

            this.refillDeadline = now + 20L;
            this.refillCloseAfter = now + 2L;
            this.refill.arm(this.context(), now, this.stepTicks("auto_totem"));
            if (this.flag("auto_totem", "openInventory")) {
               this.refillScreen = new RefillInventoryScreen(this.client.player, () -> this.cancelRefill(false));
               this.client.setScreen(this.refillScreen);
            }
         }
      }
   }

   private void advanceRefill() {
      if (this.refill.pending() || this.totemPending != 0) {
         if (this.livePlayer() && this.actionTick() >= this.refillDeadline) {
            this.cancelRefill(true);
         } else {
            boolean valid = this.livePlayer()
               && !this.runtime.settings().readOnly()
               && this.runtime.config().advantagesAcknowledged()
               && this.runtime.config().activeSettings().get("module.auto_totem").getAsBoolean()
               && this.unavailableReason("auto_totem").isEmpty()
               && this.client.level == this.totemWorld
               && this.client.player == this.totemPlayer
               && this.runtime.config().activeProfile().equals(this.totemProfile)
               && this.context().selectedSlot() == this.totemSelection
               && this.client.player.containerMenu == this.client.player.inventoryMenu
               && this.client.player.containerMenu.getCarried().isEmpty()
               && this.client.screen == this.refillScreen;
            if (!valid) {
               this.cancelRefill(false);
            } else if (this.totemPending > 0) {
               if (this.refillScreen != null && this.actionTick() >= this.refillCloseAfter) {
                  RefillInventoryScreen screen = this.refillScreen;
                  this.refillScreen = null;
                  screen.onClose();
               }

               if (this.refillConfirmed) {
                  this.cancelRefill(true);
               }
            } else if (!ItemStack.matches(this.refillOffhand, this.client.player.getOffhandItem())) {
               if (!this.prePopOffhand.is(Items.TOTEM_OF_UNDYING) || !ItemStack.matches(this.prePopOffhand, this.client.player.getOffhandItem())) {
                  this.cancelRefill(false);
               }
            } else if (!this.refill.take(this.context(), this.actionTick())) {
               if (!this.refill.pending()) {
                  this.cancelRefill(true);
               }
            } else {
               int source = -1;

               for (int i = 0; i < 36; i++) {
                  if (this.stack(i).is(Items.TOTEM_OF_UNDYING) && this.usableStack(this.stack(i))) {
                     source = i;
                     break;
                  }
               }

               if (source >= 0 && !this.client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                  this.totemPending = 20;
                  if (!this.actions.moveTotem(source)) {
                     this.cancelRefill(true);
                  }
               } else {
                  this.cancelRefill(true);
               }
            }
         }
      }
   }

   private void cancelRefill(boolean closeOwned) {
      RefillInventoryScreen screen = this.refillScreen;
      this.refillScreen = null;
      this.refill.clear();
      this.totemPending = 0;
      this.totemWorld = null;
      this.totemPlayer = null;
      this.refillOffhand = ItemStack.EMPTY;
      this.prePopOffhand = ItemStack.EMPTY;
      this.refillConfirmed = false;
      if (closeOwned && screen != null && this.client.screen == screen && this.client.player != null && this.client.player.containerMenu.getCarried().isEmpty()
         )
       {
         screen.onClose();
      }
   }

   public void totemConfirmed() {
      if (this.totemPending > 0
         && this.livePlayer()
         && this.totemWorld == this.client.level
         && this.totemPlayer == this.client.player
         && this.client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
         if (this.refillScreen != null && this.actionTick() < this.refillCloseAfter) {
            this.refillConfirmed = true;
         } else {
            this.cancelRefill(true);
         }
      }
   }

   public Set<UUID> crystalIds(BlockPos base) {
      if (this.client.level == null) {
         return Set.of();
      }

      Set<UUID> ids = new HashSet<>();

      for (EndCrystal entity : this.client.level.getEntitiesOfClass(EndCrystal.class, new AABB(base.above()).inflate(1.0), e -> true)) {
         if (Math.abs(entity.getX() - base.getX() - 0.5) < 0.01
            && Math.abs(entity.getY() - base.getY() - 1.0) < 0.01
            && Math.abs(entity.getZ() - base.getZ() - 0.5) < 0.01) {
            ids.add(entity.getUUID());
         }
      }

      return ids;
   }

   public void crystalPlaced(BlockPos base, Set<UUID> previous, InteractionHand hand, ItemStack before) {
      if (this.active("auto_crystal") && !this.sequence.busy() && before.is(Items.END_CRYSTAL)) {
         this.clearCrystal();
         this.crystalBase = base.immutable();
         this.crystalContext = this.context();
         this.crystalSource = hand == InteractionHand.MAIN_HAND ? this.context().selectedSlot() : 40;
         this.crystalBefore = before.copy();
         this.crystalAfter = before.copy();
         if (!this.client.player.isCreative()) {
            this.crystalAfter.shrink(1);
         }

         this.crystalYaw = this.client.player.getYRot();
         this.crystalPitch = this.client.player.getXRot();
         this.ownCrystal.arm(previous, this.actionTick());
      }
   }

   private void clearCrystal() {
      this.ownCrystal.clear();
      this.crystalBase = null;
      this.crystalContext = null;
      this.crystalBefore = this.crystalAfter = ItemStack.EMPTY;
   }

   private void advanceOwnCrystal() {
      if (this.ownCrystal.pending()) {
         Context now = this.context();
         if (this.active("auto_crystal")
            && !this.sequence.busy()
            && this.crystalContext.world() == now.world()
            && this.crystalContext.player() == now.player()
            && this.crystalContext.profile().equals(now.profile())
            && this.crystalContext.selectedSlot() == now.selectedSlot()
            && !(Math.abs(Mth.wrapDegrees(this.client.player.getYRot() - this.crystalYaw)) > 1.0F)
            && !(Math.abs(this.client.player.getXRot() - this.crystalPitch) > 1.0F)
            && (ItemStack.matches(this.stack(this.crystalSource), this.crystalBefore) || ItemStack.matches(this.stack(this.crystalSource), this.crystalAfter))) {
            Set<UUID> ids = this.crystalIds(this.crystalBase);
            if (ids.size() > 1) {
               this.clearCrystal();
            } else {
               if (this.client.hitResult instanceof EntityHitResult hit
                  && hit.getEntity() instanceof EndCrystal crystal
                  && ids.contains(crystal.getUUID())
                  && crystal.isAlive()
                  && this.client.player.hasLineOfSight(crystal)
                  && this.client.player.isWithinAttackRange(crystal.getBoundingBox(), 0.0)
                  && this.client.player.getEyePosition().distanceTo(hit.getLocation())
                     <= Math.min(this.number("auto_crystal", "range"), this.client.player.entityInteractionRange())
                  && !this.client.player.isUsingItem()
                  && !this.client.player.isPassenger()
                  && this.attackReady(this.client.player.getMainHandItem())) {
                  Optional<UUID> chosen = this.ownCrystal.take(ids, this.actionTick());
                  if (chosen.filter(crystal.getUUID()::equals).isPresent()) {
                     this.clearCrystal();
                     this.input.generate(this.actions::attack);
                  }
               } else {
                  this.ownCrystal.take(Set.of(), this.actionTick());
               }

               if (!this.ownCrystal.pending()) {
                  this.clearCrystal();
               }
            }
         } else {
            this.clearCrystal();
         }
      }
   }

   private ItemStack stack(int slot) {
      return this.client.player.getInventory().getItem(slot);
   }

   private boolean enchanted(ItemStack stack, ResourceKey<Enchantment> key) {
      return stack.getEnchantments().keySet().stream().anyMatch(entry -> entry.is(key) && stack.getEnchantments().getLevel(entry) > 0);
   }

   private boolean eligibleTarget() {
      return this.client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target
         ? target != this.client.player
            && target.isAlive()
            && !target.isSpectator()
            && !target.isInvisibleTo(this.client.player)
            && this.client.player.hasLineOfSight(target)
            && this.client.player.isWithinAttackRange(target.getBoundingBox(), 0.0)
            && this.client.player.getEyePosition().distanceTo(hit.getLocation()) <= this.client.player.entityInteractionRange()
         : false;
   }

   private boolean attackReady(ItemStack weapon) {
      return ((AdvantagesAttackInvoker)this.client).duckmc$getAttackCooldown() <= 0
         && !this.client.player.isHandsBusy()
         && weapon.isItemEnabled(this.client.level.enabledFeatures())
         && !this.client.player.cannotAttackWithItem(weapon, 0);
   }

   private void guarded(Runnable operation) {
      if (!this.input.generated() && this.usableInput()) {
         try {
            operation.run();
         } catch (RuntimeException error) {
            this.sequence.activeModule().ifPresent(id -> this.failures.put(id, "Action stopped safely. Restart to retry."));
            this.abort();
         }
      }
   }

   private void guarded(String id, Runnable operation) {
      if (!this.input.generated() && this.active(id)) {
         try {
            operation.run();
         } catch (RuntimeException failure) {
            this.failures.put(id, "Action stopped safely. Restart to retry.");
            this.abort();
         }
      }
   }

   public String status(String id) {
      String reason = this.unavailableReason(id);
      if (!reason.isEmpty()) {
         return reason;
      } else if (this.notices.containsKey(id)) {
         return this.notices.get(id);
      } else {
         return id.equals("informer")
            ? "Tracking " + this.throwsSeen.size() + " recent observed projectiles."
            : "Uses normal Minecraft mechanics. Conditions must be met in game.";
      }
   }

   private List<Projectile> projectiles() {
      return this.client
         .level
         .getEntitiesOfClass(
            Projectile.class, this.client.player.getBoundingBox().inflate(32.0), entity -> entity instanceof WindCharge || entity instanceof ThrownEnderpearl
         );
   }

   private void observeThrows() {
      List<Projectile> projectiles = this.projectiles();
      if (this.informerEnabled && this.informerWorld == this.client.level) {
         this.throwsSeen.expire(this.ticks);

         for (Projectile projectile : projectiles) {
            Entity owner = projectile.getOwner();
            if (projectile.tickCount <= 5
               && owner != null
               && owner.isAlive()
               && owner.level() == this.client.level
               && !owner.isInvisibleTo(this.client.player)
               && this.client.player.hasLineOfSight(owner)
               && !projectile.isInvisibleTo(this.client.player)
               && this.client.player.hasLineOfSight(projectile)) {
               String kind = projectile instanceof WindCharge ? "wind_charge" : "ender_pearl";
               if (this.throwsSeen
                  .observe(this.client.level, projectile.getUUID(), kind, this.client.player.distanceTo(owner), this.number("informer", "range"), this.ticks)) {
                  float volume = (float)this.number("informer", "volume");
                  if (!(volume <= 0.0F)) {
                     SoundEvent sound = kind.equals("wind_charge") ? SoundEvents.WIND_CHARGE_THROW : SoundEvents.ENDER_PEARL_THROW;
                     float pitch = (float)this.number("informer", kind.equals("wind_charge") ? "windPitch" : "pearlPitch");
                     this.client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
                  }
               }
            }
         }
      } else {
         this.throwsSeen.prime(this.client.level, projectiles.stream().map(Entity::getUUID).toList(), this.ticks);
         this.informerWorld = this.client.level;
         this.informerEnabled = true;
      }
   }

   private OptionalInt findHotbar(Predicate<ItemStack> matches) {
      return HotbarResolver.first(slot -> {
         ItemStack item = this.stack(slot);
         return this.usableStack(item) && matches.test(item);
      });
   }

   private boolean usableStack(ItemStack item) {
      return !item.isEmpty()
         && item.isItemEnabled(this.client.level.enabledFeatures())
         && (!item.isDamageableItem() || item.getDamageValue() < item.getMaxDamage())
         && !this.client.player.getCooldowns().isOnCooldown(item);
   }

   private int stepTicks(String id) {
      JsonElement value = this.runtime.config().activeSettings().get("advantages." + id + ".stepTicks");
      return value == null ? 1 : Math.max(1, Math.min(4, value.getAsInt()));
   }

   private long actionTick() {
      return this.client.player == null ? 0L : Integer.toUnsignedLong(this.client.player.tickCount);
   }

   private boolean sameTimedTarget() {
      return this.timedWorld == this.client.level
         && this.eligibleTarget()
         && ((EntityHitResult)this.client.hitResult).getEntity().getUUID().equals(this.timedTarget);
   }

   private boolean lungeConditions(int spearSlot, int windSlot) {
      ItemStack spear = this.stack(spearSlot);
      return this.usableStack(spear)
         && this.usableStack(this.stack(windSlot))
         && this.stack(windSlot).is(Items.WIND_CHARGE)
         && spear.is(ItemTags.SPEARS)
         && this.enchanted(spear, Enchantments.LUNGE)
         && spear.has(DataComponents.PIERCING_WEAPON)
         && (this.client.player.isCreative() || this.client.player.getFoodData().getFoodLevel() >= 6)
         && !this.client.player.isInWater()
         && !this.client.player.isUnderWater()
         && !this.client.player.isHandsBusy()
         && !this.client.player.isPassenger()
         && !this.client.player.isFallFlying();
   }

   private void prepareTimed(String id, int secondary, boolean lunge) {
      this.secondarySlot = secondary;
      this.timedLunge = lunge;
      this.timedWorld = this.client.level;
      this.timedTarget = lunge ? null : ((EntityHitResult)this.client.hitResult).getEntity().getUUID();
      this.notices.remove(id);
   }

   private void advanceTimed() {
      if (this.timed.busy()) {
         String id = this.sequence.activeModule().orElse("");
         if (id.isEmpty()) {
            this.timed.clear();
         } else if (!this.timedLunge && !this.sameTimedTarget()) {
            this.abort();
         } else {
            Action phase = this.timed.next(this.actionTick());
            if (phase == Action.EQUIP) {
               this.select(this.secondarySlot);
            } else if (phase == Action.ATTACK) {
               boolean ready = this.timedLunge
                  ? this.lungeConditions(this.secondarySlot, this.timedWindSlot)
                  : this.attackReady(this.stack(this.secondarySlot))
                     && this.usableStack(this.stack(this.secondarySlot))
                     && !this.client.player.isUsingItem()
                     && (!id.equals("auto_shield_break") || ((LivingEntity)((EntityHitResult)this.client.hitResult).getEntity()).isBlocking());
               if (!ready) {
                  this.notices.put(id, "Stopped: normal attack conditions were not ready.");
                  this.abort();
                  return;
               }

               this.input.generate(this.actions::attack);
               this.refreshOwnedItems();
            } else if (phase == Action.RESTORE || phase == Action.ABORT) {
               this.abort();
            }
         }
      }
   }

   public boolean beforeAttack() {
      if (!this.input.generated() && this.usableInput()) {
         this.cancelRefill(false);
         this.clearCrystal();
         if (!this.itemsValid()) {
            this.discard();
            return false;
         } else if (this.sequence.busy()) {
            this.abort();
            return false;
         } else {
            boolean[] consumed = new boolean[]{false};
            this.guarded(() -> {
               if (this.eligibleTarget() && !this.client.player.isUsingItem() && this.attackReady(this.client.player.getMainHandItem())) {
                  ItemStack held = this.client.player.getMainHandItem();
                  LivingEntity target = (LivingEntity)((EntityHitResult)this.client.hitResult).getEntity();
                  OptionalInt axe = this.findHotbar(s -> s.is(ItemTags.AXES));
                  OptionalInt breach = this.findHotbar(s -> s.is(Items.MACE) && this.enchanted(s, Enchantments.BREACH));
                  OptionalInt density = this.findHotbar(s -> s.is(Items.MACE));
                  boolean stun = false;
                  String id;
                  int secondary;
                  if (this.active("auto_shield_break") && target.isBlocking() && axe.isPresent() && axe.getAsInt() != this.context().selectedSlot()) {
                     secondary = axe.getAsInt();
                     id = "auto_shield_break";
                  } else if (this.active("auto_breach") && held.is(ItemTags.SWORDS) && breach.isPresent()) {
                     id = "auto_breach";
                     secondary = breach.getAsInt();
                  } else {
                     if (!this.active("stun_slam") || !held.is(ItemTags.AXES) || !density.isPresent()) {
                        if (this.active("stun_slam")) {
                           this.notices.put("stun_slam", "Hold an axe; a usable mace must be in the hotbar.");
                        }

                        if (this.active("auto_breach")) {
                           this.notices.put("auto_breach", "Hold a sword; a usable Breach mace must be in the hotbar.");
                        }

                        if (this.active("auto_shield_break")) {
                           this.notices.put("auto_shield_break", "Needs a blocking target and a usable hotbar axe.");
                        }

                        return;
                     }

                     id = "stun_slam";
                     secondary = density.getAsInt();
                     stun = true;
                  }

                  if (stun) {
                     this.stunMaceSlot = secondary;
                     this.stunTarget = target.getUUID();
                     this.stunExpires = this.actionTick();
                     this.notices.remove(id);
                  } else if (this.attackReady(this.stack(secondary)) && this.begin(id, 32, secondary)) {
                     this.prepareTimed(id, secondary, false);
                     this.select(secondary);
                     this.timed.begin(Mode.REPLACEMENT, this.stepTicks(id), this.actionTick());
                     consumed[0] = true;
                  }
               }
            });
            return consumed[0];
         }
      } else {
         return false;
      }
   }

   private boolean equipStunMace() {
      int slot = this.stunMaceSlot;
      UUID target = this.stunTarget;
      boolean live = slot >= 0 && this.actionTick() <= this.stunExpires && this.active("stun_slam");
      this.stunMaceSlot = -1;
      this.stunTarget = null;
      if (live
         && this.eligibleTarget()
         && !this.client.player.isUsingItem()
         && this.usableStack(this.stack(slot))
         && this.stack(slot).is(Items.MACE)
         && this.client.player.getMainHandItem().is(ItemTags.AXES)
         && ((EntityHitResult)this.client.hitResult).getEntity().getUUID().equals(target)) {
         this.client.player.getInventory().setSelectedSlot(slot);
         return true;
      } else {
         return false;
      }
   }

   public void afterAttack() {
      this.guarded(() -> {
         if (this.equipStunMace()) {
            this.input.generate(this.actions::attack);
         } else if (this.pendingAttack.equals("stun_slam")) {
            this.pendingAttack = "";
            if (this.itemsValid() && this.sameTimedTarget() && this.active("stun_slam")) {
               this.timed.begin(Mode.STUN, this.stepTicks("stun_slam"), this.actionTick());
            } else {
               this.abort();
            }
         }
      });
   }

   public void freshUsePressed() {
      this.input.freshPress();
      this.assistedUseOwned = false;
   }

   public boolean beforeUse() {
      if (this.input.generated() || !this.usableInput()) {
         return false;
      } else if (this.assistedUseOwned) {
         return true;
      } else if (!this.input.freshUse()) {
         return false;
      } else {
         this.cancelRefill(false);
         this.clearCrystal();
         if (this.client.player.isHandsBusy() || this.client.player.isUsingItem() || this.client.gameMode.isDestroying()) {
            return false;
         } else if (this.timedLunge && this.sequence.busy()) {
            return true;
         } else if (this.sequence.busy()) {
            this.abort();
            return false;
         } else {
            boolean[] handled = new boolean[]{false};
            this.guarded(
               () -> {
                  if (this.active("auto_lunge")
                     && AdvantagePolicy.lungePitchAllowed(this.client.player.getXRot())
                     && this.client.player.getMainHandItem().is(Items.WIND_CHARGE)) {
                     OptionalInt spear = this.findHotbar(
                        s -> s.is(ItemTags.SPEARS) && this.enchanted(s, Enchantments.LUNGE) && s.has(DataComponents.PIERCING_WEAPON)
                     );
                     int wind = this.context().selectedSlot();
                     if (spear.isPresent()
                        && this.lungeConditions(spear.getAsInt(), wind)
                        && this.attackReady(this.stack(spear.getAsInt()))
                        && this.begin("auto_lunge", 32, spear.getAsInt(), wind)) {
                        handled[0] = true;
                        this.assistedUseOwned = true;
                        this.prepareTimed("auto_lunge", spear.getAsInt(), true);
                        this.timedWindSlot = wind;
                        this.select(spear.getAsInt());
                        this.input.generate(this.actions::attack);
                        this.refreshOwnedItems();
                        this.timed.returnAfter(Math.max(3, this.stepTicks("auto_lunge")), this.actionTick());
                        return;
                     }

                     this.notices.put("auto_lunge", "Needs a ready Lunge spear, wind charge and normal jab conditions.");
                  }
               }
            );
            return handled[0];
         }
      }
   }
}
