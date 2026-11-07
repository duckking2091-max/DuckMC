package io.duckmc.input;

public record KeyBindingSpec(String action, int key, int modifiers) {
   public static final int UNBOUND = -1;
   public static final int SHIFT = 1;
   public static final int CONTROL = 2;
   public static final int ALT = 4;
   public static final int SUPER = 8;

   public KeyBindingSpec(String action, int key, int modifiers) {
      if (action != null && action.matches("[a-z_]+") && (key == -1 || key >= 32 && key <= 348) && modifiers >= 0 && modifiers <= 15) {
         this.action = action;
         this.key = key;
         this.modifiers = modifiers;
      } else {
         throw new IllegalArgumentException("Invalid key binding");
      }
   }

   public boolean unbound() {
      return this.key == -1;
   }
}
