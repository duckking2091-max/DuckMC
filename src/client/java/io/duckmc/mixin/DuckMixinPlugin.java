package io.duckmc.mixin;

import io.duckmc.fabric.CompatibilityDetector;
import io.duckmc.fabric.HookAvailability;
import io.duckmc.performance.CompatibilityPolicy.Evaluation;
import io.duckmc.performance.CompatibilityPolicy.Hook;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

@Environment(EnvType.CLIENT)
public final class DuckMixinPlugin implements IMixinConfigPlugin {
   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String target, String mixin) {
      Evaluation status = CompatibilityDetector.evaluate();
      if (mixin.endsWith("FpsLimiterMixin")) {
         return status.status(Hook.FRAME_PACING).available() || status.status(Hook.BACKGROUND_THROTTLING).available();
      } else if (!mixin.endsWith("EntityVisibilityMixin")) {
         return mixin.endsWith("DistantAnimationMixin") ? status.status(Hook.DISTANT_ANIMATION).available() : true;
      } else {
         return status.status(Hook.ENTITY_CULLING).available()
            || status.status(Hook.ENTITY_DISTANCE).available()
            || status.status(Hook.ITEM_LIMITS).available();
      }
   }

   public void acceptTargets(Set<String> own, Set<String> other) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {
   }

   public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {
      for (MethodNode method : node.methods) {
         for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)) {
               for (String hook : List.of(
                  "duckmc$frame",
                  "duckmc$cap",
                  "duckmc$visibility",
                  "duckmc$animation",
                  "duckmc$particle",
                  "duckmc$shieldSound",
                  "duckmc$shieldClear",
                  "duckmc$shieldRender",
                  "duckmc$shieldCapture",
                  "duckmc$shieldColor",
                  "duckmc$shieldPattern",
                  "duckmc$shieldEntitySound",
                  "duckmc$advantagesAttack",
                  "duckmc$advantagesAfterAttack",
                  "duckmc$advantagesUse",
                  "duckmc$advantagesFreshUse",
                  "duckmc$crystalUseStart",
                  "duckmc$crystalUseEnd",
                  "duckmc$totemInventory",
                  "duckmc$totemPop",
                  "duckmc$totemFullInventory"
               )) {
                  if (call.name.contains(hook)) {
                     HookAvailability.applied(hook);
                  }
               }
            }
         }
      }
   }
}
