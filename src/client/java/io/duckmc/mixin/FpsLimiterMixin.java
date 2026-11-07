package io.duckmc.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.performance.HookGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FramerateLimitTracker.class)
public abstract class FpsLimiterMixin {
   @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
   private void duckmc$cap(CallbackInfoReturnable<Integer> info) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null) {
         info.setReturnValue(
            (Integer)HookGuard.run(
               () -> runtime.performance().limitFps((Integer)info.getReturnValue(), Minecraft.getInstance().isWindowActive()),
               (Integer)info.getReturnValue(),
               error -> runtime.performance().failed("fps", error)
            )
         );
      }
   }
}
