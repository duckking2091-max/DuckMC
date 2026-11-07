package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.fabric.ShieldTintBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemModelResolver.class)
public abstract class ShieldItemModelMixin {
   @Inject(method = "updateForTopItem", at = @At("TAIL"), require = 0)
   private void duckmc$shieldCapture(
      ItemStackRenderState state, ItemStack stack, ItemDisplayContext display, Level world, ItemOwner context, int seed, CallbackInfo ci
   ) {
      int color = -1;
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      boolean held = display.firstPerson() || display == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || display == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      if (runtime != null && held && stack.is(Items.SHIELD) && context != null && context.asLivingEntity() instanceof Player player) {
         color = runtime.pvp().shieldTint(player, stack);
      }

      ShieldTintBridge.tint(state, color);
   }
}
