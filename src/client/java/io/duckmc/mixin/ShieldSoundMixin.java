package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ShieldSoundMixin {
   @Inject(method = "handleEntityEvent", at = @At("TAIL"), require = 0)
   private void duckmc$popStatus(ClientboundEntityEventPacket packet, CallbackInfo ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && Minecraft.getInstance().getConnection() == (Object)this) {
         runtime.pvp().observeTotemStatus(packet);
      }
   }

   @Inject(method = "handleSoundEvent", at = @At("TAIL"), require = 0)
   private void duckmc$shieldSound(ClientboundSoundPacket packet, CallbackInfo ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && Minecraft.getInstance().getConnection() == (Object)this) {
         runtime.pvp().observeShieldSound(packet);
      }
   }

   @Inject(method = "handleSoundEntityEvent", at = @At("TAIL"), require = 0)
   private void duckmc$shieldEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && Minecraft.getInstance().getConnection() == (Object)this) {
         runtime.pvp().observeShieldSound(packet);
      }
   }
}
