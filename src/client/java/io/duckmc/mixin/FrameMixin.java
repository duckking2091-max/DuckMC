package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.performance.HookGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class FrameMixin {
   @Inject(method = "runTick", at = @At("HEAD"), require = 0)
   private void duckmc$frame(boolean tick, CallbackInfo info) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null) {
         HookGuard.run(() -> {
            runtime.performance().beginFrame();
            return null;
         }, null, error -> runtime.performance().failed("frame", error));
      }
   }
}
