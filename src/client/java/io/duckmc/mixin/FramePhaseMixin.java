package io.duckmc.mixin;

import io.duckmc.performance.RenderPhases;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class FramePhaseMixin {
   private static final String TICK = "Lnet/minecraft/client/MinecraftClient;tick()V";
   private static final String RENDER = "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V";
   private static final String SWAP = "Lnet/minecraft/client/util/Window;swapBuffers(Lnet/minecraft/client/util/tracy/TracyFrameCapturer;)V";

   @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"), require = 0)
   private void duckmc$tickStart(boolean tick, CallbackInfo info) {
      RenderPhases.begin(5);
   }

   @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V", shift = Shift.AFTER), require = 0)
   private void duckmc$tickEnd(boolean tick, CallbackInfo info) {
      RenderPhases.end(5);
   }

   @Inject(
      method = "runTick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"),
      require = 0
   )
   private void duckmc$frameStart(boolean tick, CallbackInfo info) {
      RenderPhases.begin(6);
   }

   @Inject(
      method = "runTick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V", shift = Shift.AFTER),
      require = 0
   )
   private void duckmc$frameEnd(boolean tick, CallbackInfo info) {
      RenderPhases.end(6);
   }

   @Inject(
      method = "runTick",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"),
      require = 0
   )
   private void duckmc$swapStart(boolean tick, CallbackInfo info) {
      RenderPhases.begin(8);
   }

   @Inject(
      method = "runTick",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V", shift = Shift.AFTER),
      require = 0
   )
   private void duckmc$swapEnd(boolean tick, CallbackInfo info) {
      RenderPhases.end(8);
   }
}
