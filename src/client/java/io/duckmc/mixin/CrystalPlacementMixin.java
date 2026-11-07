package io.duckmc.mixin;

import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class CrystalPlacementMixin {
   @Unique
   private BlockPos duckmc$base;
   @Unique
   private Set<UUID> duckmc$previous;
   @Unique
   private ItemStack duckmc$before;
   @Unique
   private Object duckmc$world;

   @Inject(method = "useItemOn", at = @At("HEAD"))
   private void duckmc$crystalUseStart(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
      this.duckmc$base = null;
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      Minecraft client = Minecraft.getInstance();
      if (runtime != null
         && client.level != null
         && player == client.player
         && runtime.advantages().active("auto_crystal")
         && player.getItemInHand(hand).is(Items.END_CRYSTAL)) {
         BlockState state = client.level.getBlockState(hit.getBlockPos());
         if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) {
            this.duckmc$base = hit.getBlockPos().immutable();
            this.duckmc$world = client.level;
            this.duckmc$previous = runtime.advantages().crystalIds(this.duckmc$base);
            this.duckmc$before = player.getItemInHand(hand).copy();
         }
      }
   }

   @Inject(method = "useItemOn", at = @At("RETURN"))
   private void duckmc$crystalUseEnd(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
      BlockPos base = this.duckmc$base;
      this.duckmc$base = null;
      DuckMcRuntime runtime = DuckMcClient.runtimeOrNull();
      Minecraft client = Minecraft.getInstance();
      if (base != null
         && runtime != null
         && client.level == this.duckmc$world
         && client.player == player
         && ((InteractionResult)ci.getReturnValue()).consumesAction()) {
         runtime.advantages().crystalPlaced(base, this.duckmc$previous, hand, this.duckmc$before);
      }

      this.duckmc$previous = null;
      this.duckmc$before = null;
      this.duckmc$world = null;
   }
}
