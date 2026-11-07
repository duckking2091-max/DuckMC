package io.duckmc.mixin;

import io.duckmc.performance.RenderPhases;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class RenderPhaseMixin {
   @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
   private void duckmc$worldStart(CallbackInfo info) {
      RenderPhases.begin(0);
   }

   @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
   private void duckmc$worldEnd(CallbackInfo info) {
      RenderPhases.end(0);
   }

   @Inject(method = "compileSections", at = @At("HEAD"), require = 0)
   private void duckmc$chunksStart(CallbackInfo info) {
      RenderPhases.begin(1);
   }

   @Inject(method = "compileSections", at = @At("RETURN"), require = 0)
   private void duckmc$chunksEnd(CallbackInfo info) {
      RenderPhases.end(1);
   }

   @Inject(method = "applyFrustum", at = @At("HEAD"), require = 0)
   private void duckmc$frustumStart(CallbackInfo info) {
      RenderPhases.begin(2);
   }

   @Inject(method = "applyFrustum", at = @At("RETURN"), require = 0)
   private void duckmc$frustumEnd(CallbackInfo info) {
      RenderPhases.end(2);
   }

   @Inject(method = "extractVisibleEntities", at = @At("HEAD"), require = 0)
   private void duckmc$entitiesStart(CallbackInfo info) {
      RenderPhases.begin(3);
   }

   @Inject(method = "extractVisibleEntities", at = @At("RETURN"), require = 0)
   private void duckmc$entitiesEnd(CallbackInfo info) {
      RenderPhases.end(3);
   }

   @Inject(method = "prepareChunkRenders", at = @At("HEAD"), require = 0)
   private void duckmc$layersStart(CallbackInfoReturnable<ChunkSectionsToRender> info) {
      RenderPhases.begin(4);
   }

   @Inject(method = "prepareChunkRenders", at = @At("RETURN"), require = 0)
   private void duckmc$layersEnd(CallbackInfoReturnable<ChunkSectionsToRender> info) {
      RenderPhases.end(4);
   }
}
