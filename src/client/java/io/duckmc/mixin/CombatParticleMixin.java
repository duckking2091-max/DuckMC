package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ParticleEngine.class)
public abstract class CombatParticleMixin {
   @Inject(
      method = "makeParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private void duckmc$particle(ParticleOptions effect, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null) {
         try {
            if (runtime.pvp().hideParticle(effect)) {
               cir.setReturnValue(null);
            }
         } catch (RuntimeException failure) {
            runtime.pvp().failed("remove_particles", failure);
         }
      }
   }

   @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void duckmc$particleDirect(Particle particle, CallbackInfo ci) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      if (runtime != null && runtime.pvp().enabled("remove_particles")) {
         ci.cancel();
      }
   }
}
