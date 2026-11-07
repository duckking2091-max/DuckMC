package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public abstract class TotemNameMixin {
   @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
   private void duckmc$popName(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && state.nameTag != null) {
         state.nameTag = runtime.pvp().totemLabel(entity, state.nameTag);
      }
   }
}
