package io.duckmc.mixin;

import io.duckmc.performance.RenderPhases;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChunkSectionsToRender.class)
public abstract class SectionPhaseMixin {
   @Inject(method = "renderGroup", at = @At("HEAD"), require = 0)
   private void duckmc$sectionStart(CallbackInfo info) {
      RenderPhases.begin(9);
   }

   @Inject(method = "renderGroup", at = @At("RETURN"), require = 0)
   private void duckmc$sectionEnd(CallbackInfo info) {
      RenderPhases.end(9);
   }
}
