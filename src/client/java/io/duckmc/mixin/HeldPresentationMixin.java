package io.duckmc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class HeldPresentationMixin {
   @WrapOperation(
      method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
      ),
      require = 0
   )
   private void duckmc$lowHeld(
      ItemStackRenderState state,
      PoseStack matrices,
      SubmitNodeCollector queue,
      int light,
      int overlay,
      int outline,
      Operation<Void> original,
      @Local(argsOnly = true) ItemStack stack,
      @Local(argsOnly = true) ItemDisplayContext display
   ) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      boolean lower = runtime != null
         && display.firstPerson()
         && (stack.is(Items.SHIELD) && runtime.pvp().enabled("low_shield") || stack.is(Items.TOTEM_OF_UNDYING) && runtime.pvp().enabled("low_totems"));
      if (!lower) {
         original.call(new Object[]{state, matrices, queue, light, overlay, outline});
      } else {
         matrices.pushPose();

         try {
            matrices.translate(0.0, -0.22, 0.0);
            matrices.scale(0.8F, 0.8F, 0.8F);
            original.call(new Object[]{state, matrices, queue, light, overlay, outline});
         } finally {
            matrices.popPose();
         }
      }
   }
}
