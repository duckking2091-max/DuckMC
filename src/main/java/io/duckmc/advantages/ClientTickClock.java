package io.duckmc.advantages;

public final class ClientTickClock {
   private Object world;
   private Object player;
   private int age;

   public boolean observe(Object world, Object player, int age) {
      if (world == null || player == null) {
         this.world = null;
         this.player = null;
         return false;
      }

      if (this.world == world && this.player == player && this.age == age) {
         return false;
      }

      this.world = world;
      this.player = player;
      this.age = age;
      return true;
   }
}
