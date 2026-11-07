package io.duckmc.fabric;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import io.duckmc.DuckMcRuntime;
import io.duckmc.pvp.PvpPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AfterEntities;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.EndExtraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PvpWorldRenderer {
   private static final RenderStateDataKey<PvpWorldRenderer.Visuals> DATA = RenderStateDataKey.create(() -> "duckmc:pvp");
   private static boolean registered;

   private PvpWorldRenderer() {
   }

   public static boolean registered() {
      return registered;
   }

   public static void register(DuckMcRuntime runtime) {
      WorldRenderEvents.END_EXTRACTION.register((EndExtraction)context -> extract(runtime, context));
      WorldRenderEvents.AFTER_ENTITIES.register((AfterEntities)context -> draw(runtime, context));
      registered = true;
   }

   private static void extract(DuckMcRuntime runtime, WorldExtractionContext context) {
      PvpAdapter pvp = runtime.pvp();
      Minecraft client = Minecraft.getInstance();
      ArrayList<PvpWorldRenderer.Outline> outlines = new ArrayList<>();
      PvpWorldRenderer.Guide[] guide = new PvpWorldRenderer.Guide[]{null};
      if (client.player != null && client.level == context.world()) {
         JsonObject settings = runtime.config().activeSettings();
         pvp.guard(
            "hitbox_colors",
            () -> {
               for (Entity entity : context.world().entitiesForRendering()) {
                  if (outlines.size() >= 128) {
                     break;
                  }

                  if (entity != client.player
                     && entity.isAlive()
                     && !entity.isInvisibleTo(client.player)
                     && !(client.player.distanceToSqr(entity) > 1024.0)
                     && context.frustum().isVisible(entity.getBoundingBox())
                     && client.player.hasLineOfSight(entity)) {
                     outlines.add(
                        new PvpWorldRenderer.Outline(
                           entity.getBoundingBox(), settings.get(entity instanceof Player ? "pvp.playerOutlineColor" : "pvp.entityOutlineColor").getAsInt()
                        )
                     );
                  }
               }
            }
         );
         pvp.guard(
            "mace_lines",
            () -> {
               Optional<UUID> selected = pvp.currentMaceGuide();
               selected.ifPresent(
                  id -> {
                     LivingEntity target = (LivingEntity)context.world()
                        .getEntitiesOfClass(LivingEntity.class, client.player.getBoundingBox().inflate(20.0), entity -> entity.getUUID().equals(id))
                        .stream()
                        .findFirst()
                        .orElse(null);
                     if (target != null) {
                        Vector3f direction = new Vector3f(0.0F, -0.03F, -0.2F).rotate(context.camera().rotation());
                        Vec3 start = context.camera().position().add(direction.x, direction.y, direction.z);
                        guide[0] = new PvpWorldRenderer.Guide(
                           start, target.getBoundingBox().getCenter(), PvpPolicy.maceGuideColor(client.player.distanceToSqr(target)).orElseThrow()
                        );
                     }
                  }
               );
            }
         );
      }

      context.worldState().setData(DATA, new PvpWorldRenderer.Visuals(List.copyOf(outlines), Optional.ofNullable(guide[0])));
   }

   private static void draw(DuckMcRuntime runtime, WorldRenderContext context) {
      if (context.worldState() != null && context.matrices() != null && context.consumers() != null) {
         PvpWorldRenderer.Visuals data = (PvpWorldRenderer.Visuals)context.worldState().getData(DATA);
         if (data != null) {
            Vec3 camera = context.worldState().cameraRenderState.pos;
            runtime.pvp().guard("hitbox_colors", () -> {
               VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.lines());

               for (PvpWorldRenderer.Outline outline : data.outlines()) {
                  ShapeRenderer.renderShape(context.matrices(), vertices, Shapes.create(outline.box()), -camera.x, -camera.y, -camera.z, outline.color(), 1.5F);
               }
            });
            runtime.pvp()
               .guard(
                  "mace_lines",
                  () -> data.guide()
                     .ifPresent(
                        guide -> {
                           VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.lines());
                           Vec3 a = guide.start().subtract(camera);
                           Vec3 b = guide.end().subtract(camera);
                           Vec3 normal = b.subtract(a).normalize();
                           Pose matrix = context.matrices().last();
                           vertices.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z)
                              .setColor(guide.color())
                              .setNormal(matrix, (float)normal.x, (float)normal.y, (float)normal.z)
                              .setLineWidth(5.0F);
                           vertices.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z)
                              .setColor(guide.color())
                              .setNormal(matrix, (float)normal.x, (float)normal.y, (float)normal.z)
                              .setLineWidth(5.0F);
                        }
                     )
               );
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Guide(Vec3 start, Vec3 end, int color) {
   }

   @Environment(EnvType.CLIENT)
   private record Outline(AABB box, int color) {
   }

   @Environment(EnvType.CLIENT)
   private record Visuals(List<PvpWorldRenderer.Outline> outlines, Optional<PvpWorldRenderer.Guide> guide) {
   }
}
