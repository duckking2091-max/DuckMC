package io.duckmc.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public interface AdvantagesAttackInvoker {
   @Invoker("startAttack")
   boolean duckmc$invokeAttack();

   @Accessor("missTime")
   int duckmc$getAttackCooldown();

   @Accessor("rightClickDelay")
   int duckmc$getUseCooldown();
}
