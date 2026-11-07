package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.performance.HookGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public abstract class DistantAnimationMixin {
   @Inject(
      method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private void duckmc$animation(Entity entity, float tickProgress, CallbackInfoReturnable<EntityRenderState> info) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null) {
         EntityRenderState state = (EntityRenderState)HookGuard.run(
            () -> runtime.performance().animationState((EntityRenderer)(Object)this, entity, tickProgress),
            null,
            error -> runtime.performance().failed("animation", error)
         );
         if (state != null) {
            info.setReturnValue(state);
         }
      }
   }
}
