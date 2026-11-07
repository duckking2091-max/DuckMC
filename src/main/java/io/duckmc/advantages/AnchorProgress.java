package io.duckmc.advantages;

public final class AnchorProgress {
   private Object world;
   private Object position;
   private AnchorProgress.Phase phase = AnchorProgress.Phase.IDLE;
   private int spacing;
   private long deadline;
   private long due;

   public void begin(Object world, Object position, int spacing, long tick) {
      if (world != null && position != null && spacing >= 1 && spacing <= 4) {
         this.world = world;
         this.position = position;
         this.spacing = spacing;
         this.deadline = tick + 20L;
         this.due = tick;
         this.phase = AnchorProgress.Phase.WAIT_PLACED;
      } else {
         throw new IllegalArgumentException("Invalid anchor transaction");
      }
   }

   public void observe(Object world, Object position, boolean anchor, int charges, long tick) {
      if (this.phase != AnchorProgress.Phase.IDLE && this.world == world && this.position.equals(position)) {
         if (anchor && charges >= 0 && charges <= 1 && tick < this.deadline) {
            if (this.phase == AnchorProgress.Phase.WAIT_PLACED) {
               this.phase = charges == 0 ? AnchorProgress.Phase.READY_CHARGE : AnchorProgress.Phase.FAILED;
            } else if (this.phase == AnchorProgress.Phase.WAIT_CHARGED && charges == 1) {
               this.phase = AnchorProgress.Phase.READY_INTERACT;
               this.due = tick;
            }
         } else {
            this.phase = AnchorProgress.Phase.FAILED;
         }
      }
   }

   public AnchorProgress.Action next(long tick) {
      if (this.phase == AnchorProgress.Phase.IDLE) {
         return AnchorProgress.Action.NONE;
      } else if (this.phase == AnchorProgress.Phase.FAILED || tick >= this.deadline) {
         this.clear();
         return AnchorProgress.Action.ABORT;
      } else if (tick < this.due) {
         return AnchorProgress.Action.NONE;
      } else if (this.phase == AnchorProgress.Phase.READY_CHARGE) {
         this.phase = AnchorProgress.Phase.DWELL_GLOWSTONE;
         this.due = tick + this.spacing;
         return AnchorProgress.Action.EQUIP_GLOWSTONE;
      } else if (this.phase == AnchorProgress.Phase.DWELL_GLOWSTONE) {
         this.phase = AnchorProgress.Phase.WAIT_CHARGED;
         return AnchorProgress.Action.CHARGE;
      } else if (this.phase == AnchorProgress.Phase.READY_INTERACT) {
         this.phase = AnchorProgress.Phase.DWELL_ANCHOR;
         this.due = tick + this.spacing;
         return AnchorProgress.Action.EQUIP_ANCHOR;
      } else if (this.phase == AnchorProgress.Phase.DWELL_ANCHOR) {
         this.clear();
         return AnchorProgress.Action.INTERACT;
      } else {
         return AnchorProgress.Action.NONE;
      }
   }

   public void clear() {
      this.world = null;
      this.position = null;
      this.phase = AnchorProgress.Phase.IDLE;
   }

   public enum Action {
      NONE,
      EQUIP_GLOWSTONE,
      CHARGE,
      EQUIP_ANCHOR,
      INTERACT,
      ABORT;
   }

   private enum Phase {
      IDLE,
      WAIT_PLACED,
      READY_CHARGE,
      DWELL_GLOWSTONE,
      WAIT_CHARGED,
      READY_INTERACT,
      DWELL_ANCHOR,
      FAILED;
   }
}
