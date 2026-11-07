package io.duckmc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ScreenEffectRenderer.class)
public abstract class OverlayPresentationMixin {
   @Shadow
   private ItemStack itemActivationItem;

   @WrapMethod(method = "renderFire")
   private static void duckmc$lowFire(PoseStack matrices, MultiBufferSource consumers, TextureAtlasSprite sprite, Operation<Void> original) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && runtime.pvp().enabled("low_fire")) {
         matrices.pushPose();

         try {
            matrices.translate(0.0, -0.28, 0.0);
            matrices.scale(1.0F, 0.2F, 1.0F);
            original.call(new Object[]{matrices, consumers, sprite});
         } finally {
            matrices.popPose();
         }
      } else {
         original.call(new Object[]{matrices, consumers, sprite});
      }
   }

   @WrapOperation(
      method = "renderItemActivationAnimation",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
      ),
      require = 0
   )
   private void duckmc$lowPop(
      ItemStackRenderState state, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, int outline, Operation<Void> original
   ) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      boolean small = runtime != null
         && this.itemActivationItem != null
         && this.itemActivationItem.is(Items.TOTEM_OF_UNDYING)
         && runtime.pvp().enabled("low_totems");
      if (!small) {
         original.call(new Object[]{state, matrices, queue, light, overlay, outline});
      } else {
         matrices.pushPose();

         try {
            matrices.scale(0.2F, 0.2F, 0.2F);
            original.call(new Object[]{state, matrices, queue, light, overlay, outline});
         } finally {
            matrices.popPose();
         }
      }
   }
}
