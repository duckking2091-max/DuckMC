package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class AdvantagesInputMixin {
   @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 0))
   private void duckmc$advantagesFreshUse(CallbackInfo ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null) {
         runtime.advantages().freshUsePressed();
      }
   }

   @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
   private void duckmc$advantagesAttack(CallbackInfoReturnable<Boolean> ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && runtime.advantages().beforeAttack()) {
         ci.setReturnValue(false);
      }
   }

   @Inject(method = "startAttack", at = @At("RETURN"))
   private void duckmc$advantagesAfterAttack(CallbackInfoReturnable<Boolean> ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null) {
         runtime.advantages().afterAttack();
      }
   }

   @Inject(
      method = "startUseItem",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I", opcode = 181, shift = Shift.AFTER),
      cancellable = true
   )
   private void duckmc$advantagesUse(CallbackInfo ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && runtime.advantages().beforeUse()) {
         ci.cancel();
      }
   }
}
