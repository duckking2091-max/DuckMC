package io.duckmc.advantages;

public final class PopRefill {
   private SlotSequence.Context origin;
   private long due;
   private long deadline;

   public void arm(SlotSequence.Context context, long tick, int delay) {
      if (delay >= 1 && delay <= 4) {
         this.origin = context;
         this.due = tick + delay;
         this.deadline = tick + 20L;
      } else {
         throw new IllegalArgumentException("Invalid refill delay");
      }
   }

   public boolean take(SlotSequence.Context context, long tick) {
      if (this.origin == null) {
         return false;
      }

      if (!context.usable()
         || this.origin.world() != context.world()
         || this.origin.player() != context.player()
         || !this.origin.profile().equals(context.profile())
         || this.origin.selectedSlot() != context.selectedSlot()
         || tick >= this.deadline) {
         this.clear();
         return false;
      }

      if (tick < this.due) {
         return false;
      }

      this.clear();
      return true;
   }

   public void clear() {
      this.origin = null;
   }

   public boolean pending() {
      return this.origin != null;
   }
}
