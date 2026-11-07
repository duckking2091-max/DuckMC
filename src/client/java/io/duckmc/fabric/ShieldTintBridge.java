package io.duckmc.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.pvp.ShieldTint;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public final class ShieldTintBridge {
   private ShieldTintBridge() {
   }

   public static void verifyRenderHook() {
      try {
         ItemStackRenderState state = new ItemStackRenderState();
         state.newLayer();
         state.submit(new PoseStack(), null, 0, 0, 0);
      } catch (RuntimeException var1) {
      }
   }

   public static void tint(ItemStackRenderState state, int color) {
      if (state instanceof ShieldTintBridge.State tinted) {
         tinted.duckmc$setTint(color);
      }
   }

   public static void withTint(int color, Runnable render) {
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      ShieldTint.withTint(runtime != null && runtime.pvp().enabled("shield_status") ? color : -1, render);
   }

   public static int multiply(int original, int tint) {
      return ShieldTint.multiply(original, tint);
   }

   public static int color(int original) {
      return multiply(original, ShieldTint.current());
   }

   @Environment(EnvType.CLIENT)
   public interface State {
      int duckmc$getTint();

      void duckmc$setTint(int var1);
   }
}
