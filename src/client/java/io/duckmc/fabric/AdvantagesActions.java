package io.duckmc.fabric;

import io.duckmc.mixin.AdvantagesAttackInvoker;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class AdvantagesActions {
   private final Minecraft client;

   public AdvantagesActions(Minecraft client) {
      this.client = client;
   }

   public void select(OptionalInt slot) {
      if (this.client.player != null && slot.isPresent()) {
         this.client.player.getInventory().setSelectedSlot(slot.getAsInt());
      }
   }

   public boolean attack() {
      return ((AdvantagesAttackInvoker)this.client).duckmc$invokeAttack();
   }

   public boolean moveTotem(int inventorySlot) {
      if (this.client.player != null && this.client.gameMode != null && inventorySlot >= 0 && inventorySlot < 36) {
         LocalPlayer player = this.client.player;
         if (player.containerMenu == player.inventoryMenu
            && player.containerMenu.getCarried().isEmpty()
            && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
            && player.getInventory().getItem(inventorySlot).is(Items.TOTEM_OF_UNDYING)) {
            int screenSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
            this.client.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, screenSlot, 40, ClickType.SWAP, player);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
