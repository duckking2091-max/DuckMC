package io.duckmc.advantages;

import java.util.Optional;
import java.util.OptionalInt;

public final class SlotSequence {
   private SlotSequence.Context origin;
   private String module;
   private int ownedSlot;
   private int remaining;

   public boolean begin(String module, SlotSequence.Context context, int durationTicks) {
      if (!AdvantagePolicy.MODULES.contains(module) || durationTicks < 1 || durationTicks > 32) {
         throw new IllegalArgumentException("Invalid sequence");
      } else if (!this.busy() && this.valid(context)) {
         this.origin = context;
         this.module = module;
         this.ownedSlot = context.selectedSlot();
         this.remaining = durationTicks;
         return true;
      } else {
         return false;
      }
   }

   public OptionalInt select(SlotSequence.Context context, int slot) {
      if (slot < 0 || slot > 8) {
         throw new IllegalArgumentException("Invalid hotbar slot");
      }

      if (!this.matches(context)) {
         this.clear();
         return OptionalInt.empty();
      }

      if (slot == this.ownedSlot) {
         return OptionalInt.empty();
      }

      this.ownedSlot = slot;
      return OptionalInt.of(slot);
   }

   public OptionalInt tick(SlotSequence.Context context) {
      return this.tick(context, true);
   }

   public OptionalInt tick(SlotSequence.Context context, boolean advanceTime) {
      if (!this.busy()) {
         return OptionalInt.empty();
      } else if (!this.matches(context)) {
         this.clear();
         return OptionalInt.empty();
      } else {
         return advanceTime && --this.remaining <= 0 ? this.abort(context) : OptionalInt.empty();
      }
   }

   public OptionalInt abort(SlotSequence.Context context) {
      OptionalInt result = this.matches(context) && this.ownedSlot != this.origin.selectedSlot()
         ? OptionalInt.of(this.origin.selectedSlot())
         : OptionalInt.empty();
      this.clear();
      return result;
   }

   public boolean busy() {
      return this.origin != null;
   }

   public Optional<String> activeModule() {
      return Optional.ofNullable(this.module);
   }

   private boolean matches(SlotSequence.Context context) {
      return this.busy()
         && this.valid(context)
         && context.world() == this.origin.world()
         && context.player() == this.origin.player()
         && context.profile().equals(this.origin.profile())
         && context.selectedSlot() == this.ownedSlot;
   }

   private boolean valid(SlotSequence.Context context) {
      return context != null
         && context.usable()
         && context.world() != null
         && context.player() != null
         && context.profile() != null
         && context.selectedSlot() >= 0
         && context.selectedSlot() < 9;
   }

   private void clear() {
      this.origin = null;
      this.module = null;
      this.remaining = 0;
   }

   public record Context(Object world, Object player, String profile, int selectedSlot, boolean usable) {
   }
}
