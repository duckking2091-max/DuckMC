package io.duckmc.mixin;

import io.duckmc.performance.RenderPhases;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class HudPhaseMixin {
   @Inject(method = "render", at = @At("HEAD"), require = 0)
   private void duckmc$hudStart(CallbackInfo info) {
      RenderPhases.begin(7);
   }

   @Inject(method = "render", at = @At("RETURN"), require = 0)
   private void duckmc$hudEnd(CallbackInfo info) {
      RenderPhases.end(7);
   }
}
