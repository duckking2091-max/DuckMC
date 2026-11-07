package io.duckmc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.duckmc.fabric.HookAvailability;
import io.duckmc.fabric.ShieldTintBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemStackRenderState.class)
public abstract class ShieldItemStateMixin implements ShieldTintBridge.State {
   @Unique
   private int duckmc$tint = -1;

   @Override
   public int duckmc$getTint() {
      return this.duckmc$tint;
   }

   @Override
   public void duckmc$setTint(int tint) {
      this.duckmc$tint = tint;
   }

   @Inject(method = "clear", at = @At("HEAD"), require = 0)
   private void duckmc$shieldClear(CallbackInfo ci) {
      this.duckmc$tint = -1;
   }

   @WrapOperation(
      method = "submit",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
      ),
      require = 0
   )
   private void duckmc$shieldRender(
      LayerRenderState layer, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, int outline, Operation<Void> original
   ) {
      HookAvailability.applied("duckmc$shieldRender");
      ShieldTintBridge.withTint(this.duckmc$tint, () -> original.call(new Object[]{layer, matrices, queue, light, overlay, outline}));
   }
}
