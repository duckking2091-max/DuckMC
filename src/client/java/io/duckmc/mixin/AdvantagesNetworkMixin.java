package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class AdvantagesNetworkMixin {
   @Inject(method = "handleEntityEvent", at = @At("TAIL"))
   private void duckmc$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (this.duckmc$currentHandler() && client.level != null && packet.getEventId() == 35 && packet.getEntity(client.level) == client.player) {
         DuckMcClient.runtime().advantages().totemPopped();
      }
   }

   private boolean duckmc$currentHandler() {
      return (Object)this == Minecraft.getInstance().getConnection() && DuckMcClient.runtimeOrNull() != null;
   }

   @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
   private void duckmc$totemInventory(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (this.duckmc$currentHandler()
         && player != null
         && packet.getContainerId() == player.inventoryMenu.containerId
         && packet.getSlot() == 45
         && packet.getItem().is(Items.TOTEM_OF_UNDYING)) {
         DuckMcClient.runtime().advantages().totemConfirmed();
      }
   }

   @Inject(method = "handleContainerContent", at = @At("TAIL"))
   private void duckmc$totemFullInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (this.duckmc$currentHandler()
         && player != null
         && packet.containerId() == player.inventoryMenu.containerId
         && packet.items().size() > 45
         && ((ItemStack)packet.items().get(45)).is(Items.TOTEM_OF_UNDYING)) {
         DuckMcClient.runtime().advantages().totemConfirmed();
      }
   }
}
