package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.performance.HookGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityVisibilityMixin {
   @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true, require = 0)
   private void duckmc$visibility(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> info) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && (Boolean)info.getReturnValue()) {
         info.setReturnValue(
            (Boolean)HookGuard.run(
               () -> runtime.performance().shouldRender(entity, frustum, x, y, z), true, error -> runtime.performance().failed("visibility", error)
            )
         );
      }
   }
}
