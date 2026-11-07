package io.duckmc.advantages;

public final class TimedSwap {
   private int phase;
   private int delay;
   private long due;

   public void returnAfter(int delay, long tick) {
      if (delay >= 1 && delay <= 4 && !this.busy()) {
         this.delay = delay;
         this.due = tick + delay;
         this.phase = 3;
      } else {
         throw new IllegalArgumentException("Invalid or overlapping return");
      }
   }

   public TimedSwap.Action begin(TimedSwap.Mode mode, int delay, long tick) {
      if (mode != null && delay >= 1 && delay <= 4 && !this.busy()) {
         this.delay = delay;
         this.due = tick + delay;
         this.phase = mode == TimedSwap.Mode.STUN ? 1 : 2;
         return mode == TimedSwap.Mode.STUN ? TimedSwap.Action.NONE : TimedSwap.Action.EQUIP;
      } else {
         throw new IllegalArgumentException("Invalid or overlapping timed swap");
      }
   }

   public TimedSwap.Action next(long tick) {
      return this.next(tick, true, this.delay);
   }

   public TimedSwap.Action next(long tick, boolean attackReady, int returnDelay) {
      if (returnDelay < 1 || returnDelay > 4) {
         throw new IllegalArgumentException("Invalid return delay");
      }

      if (!this.busy() || tick < this.due) {
         return TimedSwap.Action.NONE;
      }

      if (this.phase == 2 && !attackReady) {
         return TimedSwap.Action.NONE;
      }

      this.due = tick + (this.phase == 2 ? returnDelay : this.delay);

      return switch (this.phase++) {
         case 1 -> TimedSwap.Action.EQUIP;
         case 2 -> TimedSwap.Action.ATTACK;
         default -> {
            this.clear();
            yield TimedSwap.Action.RESTORE;
         }
      };
   }

   public void clear() {
      this.phase = 0;
   }

   public boolean busy() {
      return this.phase != 0;
   }

   public enum Action {
      NONE,
      EQUIP,
      ATTACK,
      RESTORE,
      ABORT;
   }

   public enum Mode {
      STUN,
      REPLACEMENT;
   }
}
