package io.duckmc.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
final class RefillInventoryScreen extends InventoryScreen {
   private final Runnable manualInput;

   RefillInventoryScreen(Player player, Runnable manualInput) {
      super(player);
      this.manualInput = manualInput;
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.manualInput.run();
      return super.mouseClicked(click, doubled);
   }

   public boolean keyPressed(KeyEvent key) {
      this.manualInput.run();
      return super.keyPressed(key);
   }

   public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      this.manualInput.run();
      return super.mouseScrolled(x, y, horizontal, vertical);
   }
}
